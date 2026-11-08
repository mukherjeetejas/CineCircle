package com.cinecircle.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void disabledWithoutKeyAndNeverCallsTheApi() {
        AtomicReference<ClientRequest> seen = new AtomicReference<>();
        LlmClient client = client("  ", req -> {
            seen.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });
        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.completeJson("s", "u", 10, Duration.ofSeconds(1)))
                .isInstanceOf(LlmException.class);
        assertThat(seen.get()).isNull();
    }

    @Test
    void sendsAnthropicMessagesRequestAndReturnsText() throws Exception {
        AtomicReference<ClientRequest> seen = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        LlmClient client = client("sk-test-key", req -> {
            seen.set(req);
            body.set(bodyOf(req));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"ok\\\":\"},"
                            + "{\"type\":\"text\",\"text\":\"true}\"}],\"stop_reason\":\"end_turn\"}")
                    .build());
        });

        String text = client.completeJson("SYS", "USER", 150, Duration.ofSeconds(2));

        assertThat(text).isEqualTo("{\"ok\":true}");
        ClientRequest req = seen.get();
        assertThat(req.method()).isEqualTo(HttpMethod.POST);
        assertThat(req.url().toString()).isEqualTo("http://llm.test/v1/messages");
        assertThat(req.headers().getFirst("x-api-key")).isEqualTo("sk-test-key");
        assertThat(req.headers().getFirst("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(req.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        JsonNode json = mapper.readTree(body.get());
        assertThat(json.get("model").asText()).isEqualTo("test-model");
        assertThat(json.get("max_tokens").asInt()).isEqualTo(150);
        assertThat(json.get("system").asText()).isEqualTo("SYS");
        assertThat(json.get("messages").get(0).get("role").asText()).isEqualTo("user");
        assertThat(json.get("messages").get(0).get("content").asText()).isEqualTo("USER");
        assertThat(json.get("thinking").get("type").asText()).isEqualTo("disabled");
    }

    @Test
    void httpErrorBecomesLlmExceptionWithoutLeakingKey() {
        LlmClient client = client("sk-secret", req -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":{\"type\":\"authentication_error\"}}").build()));
        assertThatThrownBy(() -> client.completeJson("s", "u", 10, Duration.ofSeconds(2)))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("401")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("sk-secret"));
    }

    @Test
    void timeoutBecomesLlmException() {
        LlmClient client = client("sk-test", req -> Mono.never());
        long start = System.nanoTime();
        assertThatThrownBy(() -> client.completeJson("s", "u", 10, Duration.ofMillis(200)))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("timeout");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void replyWithoutTextIsRejected() {
        LlmClient client = client("sk-test", req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"content\":[]}").build()));
        assertThatThrownBy(() -> client.completeJson("s", "u", 10, Duration.ofSeconds(2)))
                .isInstanceOf(LlmException.class);
    }

    @Test
    void thinkingFieldOmittedWhenPropertyIsFalse() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        LlmClient client = client("sk-test", req -> {
            body.set(bodyOf(req));
            return Mono.just(ok("{\"content\":[{\"type\":\"text\",\"text\":\"{}\"}],\"stop_reason\":\"end_turn\"}"));
        }, false);
        client.completeJson("s", "u", 10, Duration.ofSeconds(2));
        assertThat(mapper.readTree(body.get()).has("thinking")).isFalse();
    }

    @Test
    void maxTokensStopReasonThrows() {
        // the observed failure: thinking ate the budget, the JSON text is cut off
        LlmClient client = client("sk-test", req -> Mono.just(ok("{\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},"
                + "{\"type\":\"text\",\"text\":\"{\\\"is\"}],\"stop_reason\":\"max_tokens\"}")));
        assertThatThrownBy(() -> client.completeJson("s", "u", 150, Duration.ofSeconds(2)))
                .isInstanceOf(LlmException.class)
                .hasMessage("LLM reply truncated");
    }

    @Test
    void refusalStopReasonThrows() {
        LlmClient client = client("sk-test", req -> Mono.just(ok("{\"content\":[{\"type\":\"text\",\"text\":\"\"}],"
                + "\"stop_reason\":\"refusal\"}")));
        assertThatThrownBy(() -> client.completeJson("s", "u", 150, Duration.ofSeconds(2)))
                .isInstanceOf(LlmException.class)
                .hasMessage("LLM refused");
    }

    @Test
    void leadingThinkingBlockIsSkipped() {
        LlmClient client = client("sk-test", req -> Mono.just(ok("{\"content\":["
                + "{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"abc\"},"
                + "{\"type\":\"redacted_thinking\",\"data\":\"xyz\"},"
                + "{\"type\":\"text\",\"text\":\"{\\\"isSpoiler\\\":false}\"}],\"stop_reason\":\"end_turn\"}")));
        assertThat(client.completeJson("s", "u", 150, Duration.ofSeconds(2))).isEqualTo("{\"isSpoiler\":false}");
    }

    private static ClientResponse ok(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json).build();
    }

    @Test
    void propertiesToStringRedactsKey() {
        assertThat(new LlmProperties("sk-secret", "m", "u", true).toString()).doesNotContain("sk-secret");
    }

    @Test
    void llmJsonToleratesCodeFences() {
        JsonNode n = LlmJson.parseObject(mapper, "```json\n{\"a\":1}\n```");
        assertThat(n.get("a").asInt()).isEqualTo(1);
        assertThatThrownBy(() -> LlmJson.parseObject(mapper, "no json here")).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> LlmJson.parseObject(mapper, "{broken")).isInstanceOf(LlmException.class);
    }

    private LlmClient client(String key, org.springframework.web.reactive.function.client.ExchangeFunction fn) {
        return client(key, fn, true);
    }

    private LlmClient client(String key, org.springframework.web.reactive.function.client.ExchangeFunction fn,
                             boolean thinkingDisabled) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(fn)
                .exchangeStrategies(ExchangeStrategies.withDefaults());
        return new LlmClient(builder, new LlmProperties(key, "test-model", "http://llm.test", thinkingDisabled));
    }

    @SuppressWarnings("unchecked")
    private static String bodyOf(ClientRequest req) {
        MockClientHttpRequest mock = new MockClientHttpRequest(HttpMethod.POST, req.url());
        BodyInserter<?, ? super ClientHttpRequest> inserter = req.body();
        inserter.insert(mock, new BodyInserter.Context() {
            @Override
            public java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Map<String, Object> hints() {
                return java.util.Map.of();
            }
        }).block();
        return mock.getBodyAsString().block();
    }
}
