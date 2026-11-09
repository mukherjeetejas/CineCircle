package com.cinecircle.mcp.tools;

import com.cinecircle.mcp.auth.CallerResolver;
import com.cinecircle.mcp.auth.HttpRequestInfo;
import com.cinecircle.mcp.auth.RateLimiter;
import com.cinecircle.mcp.client.CineCircleApiClient;
import com.cinecircle.mcp.config.CineCircleProperties;
import com.cinecircle.mcp.error.ToolFailure;
import com.cinecircle.mcp.format.Links;
import com.cinecircle.mcp.format.MovieFormatter;
import com.cinecircle.mcp.format.ReviewFormatter;
import com.cinecircle.mcp.format.UserFormatter;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.junit.jupiter.api.AfterEach;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wires the real client, formatters and tool classes against a {@link MockRestServiceServer}
 * (no Spring context, no network, no real sleeping).
 */
abstract class ToolTestSupport {

    static final String API = "http://localhost:8080/api";
    static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.test-secret-token.sig";
    static final String WAKING_UP_UNREACHABLE =
            "CineCircle is waking up — try again in a minute. (CineCircle API at http://localhost:8080/api: not reachable)";

    /** A local call: stdio or direct, no MCP HTTP transport context. */
    static final ToolContext LOCAL = null;

    MockRestServiceServer server;
    CineCircleApiClient api;
    MovieTools movieTools;
    ReviewTools reviewTools;
    UserTools userTools;
    /** Delays the client asked to sleep for (the wake-up retry); recorded instead of slept. */
    final List<Duration> sleeps = new CopyOnWriteArrayList<>();

    /** Builds everything; {@code token == null} means CINECIRCLE_TOKEN is not set. Env token allowed, no rate limit. */
    void setUp(String token) {
        setUp(CineCircleProperties.of(API, token, null), new RateLimiter(0, Clock.systemUTC()));
    }

    void setUp(CineCircleProperties props, RateLimiter rateLimiter) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sleeps.clear();
        api = new CineCircleApiClient(builder, props, sleeps::add);
        Links links = new Links(props);
        ToolRunner runner = new ToolRunner(props, new CallerResolver(props), rateLimiter);
        movieTools = new MovieTools(api, new MovieFormatter(links), runner);
        reviewTools = new ReviewTools(api, new ReviewFormatter(links), new UserFormatter(links), runner);
        userTools = new UserTools(api, new UserFormatter(links), runner);
    }

    @AfterEach
    void verifyServer() {
        if (server != null) {
            server.verify();
        }
    }

    /**
     * The ToolContext Spring AI builds for a tool call that arrived over MCP HTTP: a per-request exchange whose
     * transport context holds what {@link HttpRequestInfo#EXTRACTOR} read from that request.
     */
    static ToolContext http(String bearerToken, String clientIp) {
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.transportContext()).thenReturn(McpTransportContext.create(
                Map.of(HttpRequestInfo.CONTEXT_KEY, new HttpRequestInfo(bearerToken, clientIp))));
        return new ToolContext(Map.of(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, exchange));
    }

    /** Runs a tool call that must fail and returns the error text shown to the AI. */
    static String toolError(Runnable call) {
        ToolFailure f = catchThrowableOfType(ToolFailure.class, call::run);
        assertThat(f).as("expected the tool to fail").isNotNull();
        return f.getMessage();
    }

    static String errorJson(int status, String code, String message) {
        return "{\"status\":" + status + ",\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
    }

    static String user(String username, boolean isPrivate) {
        return "{\"id\":\"u-" + username + "\",\"username\":\"" + username + "\",\"displayName\":\"" + username
                + " D\",\"avatarUrl\":null,\"isPrivate\":" + isPrivate + "}";
    }

    static String spoiler(boolean flagged, String source, String aiStatus, String aiReason, boolean titleHidden) {
        return "{\"flagged\":" + flagged + ",\"source\":\"" + source + "\",\"aiStatus\":\"" + aiStatus
                + "\",\"aiReason\":" + (aiReason == null ? "null" : "\"" + aiReason + "\"")
                + ",\"titleHidden\":" + titleHidden + "}";
    }

    static String noSpoiler() {
        return spoiler(false, "NONE", "CLEAN", null, false);
    }

    /** A PostSummary JSON item. */
    static String post(String id, String title, String body, int rating, boolean authorFlag, String spoilerJson) {
        return "{\"id\":\"" + id + "\",\"author\":" + user("alice", false)
                + ",\"movie\":{\"tmdbId\":438631,\"title\":\"Dune\",\"releaseYear\":2021,\"posterUrl\":\"https://image.tmdb.org/t/p/w500/d.jpg\"}"
                + ",\"title\":\"" + title + "\",\"bodyPreview\":\"" + body + "\",\"rating\":" + rating
                + ",\"containsSpoilers\":" + authorFlag + ",\"spoiler\":" + spoilerJson
                + ",\"imageUrl\":null,\"upvoteCount\":12,\"commentCount\":1,\"upvotedByMe\":false,\"isMine\":false"
                + ",\"createdAt\":\"2026-11-01T10:00:00Z\"}";
    }

    static String page(boolean hasNext, String... items) {
        return "{\"items\":[" + String.join(",", items) + "],\"page\":0,\"size\":10,\"hasNext\":" + hasNext + "}";
    }
}
