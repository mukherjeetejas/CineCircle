package com.cinecircle.llm;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Shared client for the Anthropic Messages API ({@code POST /v1/messages}) on the existing WebClient stack
 * (no SDK dependency). Used by the review summary and the spoiler detector.
 *
 * <p>{@link #isEnabled()} is false when {@code app.llm.api-key} is blank; the app then runs with both AI
 * features switched off. Logs contain at most the HTTP status / error class - never the key, prompts or
 * review text.
 */
@Slf4j
@Component
public class LlmClient implements DisposableBean {

    static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final ConnectionProvider connectionProvider;
    private final boolean enabled;
    private final String model;
    private final boolean thinkingDisabled;
    private final LlmUsageService usage;

    public LlmClient(WebClient.Builder builder, LlmProperties props, LlmUsageService usage) {
        this.usage = usage;
        this.enabled = props.apiKey() != null && !props.apiKey().isBlank();
        this.thinkingDisabled = props.thinkingDisabled();
        this.model = props.model() == null || props.model().isBlank() ? "claude-haiku-5-5" : props.model();
        if (!enabled) {
            log.warn("CINECIRCLE_LLM_API_KEY is not set: AI review summaries and the AI spoiler detector are "
                    + "disabled. Everything else works normally.");
        } else {
            log.info("LLM features enabled (model {})", model);
        }
        this.connectionProvider = ConnectionProvider.builder("llm")
                .maxConnections(20)
                .maxIdleTime(Duration.ofSeconds(30))
                .evictInBackground(Duration.ofSeconds(60))
                .build();
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis());
        String baseUrl = props.baseUrl() == null || props.baseUrl().isBlank()
                ? "https://api.anthropic.com" : props.baseUrl();
        // If the builder already carries an exchangeFunction (tests), it takes precedence over the connector.
        this.webClient = builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("x-api-key", enabled ? props.apiKey() : "")
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String model() {
        return model;
    }

    /**
     * Sends one user message with a system prompt and returns the model's text reply (text blocks
     * concatenated). The caller parses and validates the JSON.
     *
     * <p>Before the call, one unit of today's {@code app.llm.daily-call-limit} is reserved (atomic {@code $inc} in
     * {@code llm_usage}); when the limit is used up the API is not called and {@link LlmCapReachedException} is
     * thrown - the LLM behaves as disabled for this call.
     *
     * @throws LlmCapReachedException when today's AI call limit is used up (not a failed attempt)
     * @throws LlmException           when disabled, on timeout, transport or API error, or an empty reply
     */
    public String completeJson(String system, String user, int maxTokens, Duration timeout) {
        if (!enabled) {
            throw new LlmException("LLM is disabled (no API key configured)");
        }
        boolean allowed;
        try {
            allowed = usage.tryAcquire();
        } catch (LlmException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI usage counter unavailable ({})", e.getClass().getSimpleName());
            throw new LlmException("AI usage counter unavailable", e);
        }
        if (!allowed) {
            throw new LlmCapReachedException();
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("max_tokens", maxTokens);
        request.put("system", system);
        request.put("messages", List.of(Map.of("role", "user", "content", user)));
        if (thinkingDisabled) {
            // thinking tokens count toward max_tokens; with small budgets they truncate the JSON reply
            request.put("thinking", Map.of("type", "disabled"));
        }
        JsonNode response;
        try {
            response = webClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(timeout)
                    .block(timeout.plusSeconds(1));
        } catch (WebClientResponseException e) {
            // status only: the error body is not logged
            log.warn("LLM API returned HTTP {}", e.getStatusCode().value());
            throw new LlmException("LLM API returned HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            boolean timedOut = hasCause(e, TimeoutException.class)
                    || (e instanceof IllegalStateException && String.valueOf(e.getMessage()).contains("Timeout"));
            String reason = timedOut ? "timeout after " + timeout.toMillis() + " ms" : e.getClass().getSimpleName();
            log.warn("LLM request failed: {}", reason);
            throw new LlmException("LLM request failed: " + reason, e);
        }
        return extractText(response);
    }

    static String extractText(JsonNode response) {
        if (response == null) {
            throw new LlmException("LLM reply has no content");
        }
        String stopReason = response.path("stop_reason").asText("");
        if ("max_tokens".equals(stopReason)) {
            log.info("LLM reply truncated (max_tokens)");
            throw new LlmException("LLM reply truncated");
        }
        if ("refusal".equals(stopReason)) {
            log.info("LLM refused");
            throw new LlmException("LLM refused");
        }
        if (!response.path("content").isArray()) {
            throw new LlmException("LLM reply has no content");
        }
        StringBuilder sb = new StringBuilder();
        // only text blocks; thinking / redacted_thinking and any other block types are skipped
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText()) && block.hasNonNull("text")) {
                sb.append(block.get("text").asText());
            }
        }
        if (sb.isEmpty()) {
            throw new LlmException("LLM reply has no text");
        }
        return sb.toString();
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        connectionProvider.dispose();
    }
}
