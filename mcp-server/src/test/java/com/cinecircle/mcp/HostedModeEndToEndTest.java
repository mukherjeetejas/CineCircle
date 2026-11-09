package com.cinecircle.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MCP Streamable HTTP server on a random port + a fake CineCircle backend (JDK HttpServer) that records
 * which Authorization header each backend request carried. Proves that the token comes from each incoming MCP
 * request and that concurrent callers with different tokens never get mixed up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HostedModeEndToEndTest {

    static final String TOKEN_A = "eyJ.alice-e2e-token.sigA";
    static final String TOKEN_B = "eyJ.bob-e2e-token.sigB";
    static final String ENV_TOKEN = "eyJ.operator-env-token.sig";
    static final int CALLS_PER_CLIENT = 15;

    /** postId → Authorization header the backend received ("<none>" if missing). */
    static final ConcurrentLinkedQueue<String[]> upvotes = new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<String> profileAuth = new ConcurrentLinkedQueue<>();
    static HttpServer backend;

    @LocalServerPort
    int port;

    final List<McpSyncClient> clients = new ArrayList<>();

    @BeforeAll
    static void startBackend() throws IOException {
        backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.setExecutor(Executors.newFixedThreadPool(16));
        backend.createContext("/api/posts/", ex -> {
            String path = ex.getRequestURI().getPath(); // /api/posts/{id}/upvote
            String id = path.substring("/api/posts/".length(), path.lastIndexOf('/'));
            upvotes.add(new String[] {id, auth(ex)});
            sleepQuietly(15); // keep requests in flight so the two clients really overlap
            respond(ex, 200, "{\"upvoted\":true,\"upvoteCount\":1}");
        });
        backend.createContext("/api/users/", ex -> {
            profileAuth.add(auth(ex));
            respond(ex, 200, "{\"id\":\"u1\",\"username\":\"carol\",\"displayName\":\"Carol\",\"avatarUrl\":null,"
                    + "\"isPrivate\":false,\"bio\":null,\"followerCount\":0,\"followingCount\":0,\"postCount\":0,"
                    + "\"createdAt\":\"2026-01-02T00:00:00Z\",\"followStatus\":\"NONE\",\"canViewPosts\":true}");
        });
        backend.start();
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("cinecircle.api-url", () -> "http://127.0.0.1:" + backend.getAddress().getPort() + "/api");
        r.add("cinecircle.token", () -> ENV_TOKEN);           // present on the server ...
        r.add("cinecircle.allow-env-token", () -> "false");   // ... but hosted mode must ignore it
        r.add("cinecircle.rate-limit-per-minute", () -> "1000");
    }

    @AfterEach
    void closeClients() {
        clients.forEach(McpSyncClient::closeGracefully);
        upvotes.clear();
        profileAuth.clear();
    }

    private McpSyncClient client(String token) {
        HttpClientStreamableHttpTransport.Builder transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port).endpoint("/mcp");
        if (token != null) {
            transport.customizeRequest(b -> b.header("Authorization", "Bearer " + token));
        }
        McpSyncClient c = McpClient.sync(transport.build()).requestTimeout(Duration.ofSeconds(30)).build();
        c.initialize();
        clients.add(c);
        return c;
    }

    private static String text(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    @Test
    void health_isOk_withoutAuth() throws Exception {
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"status\":\"ok\"");
    }

    @Test
    void toolsAreListed_atTheMcpEndpoint() {
        assertThat(client(null).listTools().tools()).hasSize(10);
    }

    @Test
    void twoConcurrentClients_withDifferentTokens_eachActAsThemselves() throws Exception {
        McpSyncClient alice = client(TOKEN_A);
        McpSyncClient bob = client(TOKEN_B);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<McpSchema.CallToolResult>> results = new ArrayList<>();
        for (int i = 0; i < CALLS_PER_CLIENT; i++) {
            String a = "a-" + i;
            String b = "b-" + i;
            results.add(pool.submit(() -> {
                start.await();
                return alice.callTool(new McpSchema.CallToolRequest("upvote_review", Map.of("postId", a)));
            }));
            results.add(pool.submit(() -> {
                start.await();
                return bob.callTool(new McpSchema.CallToolRequest("upvote_review", Map.of("postId", b)));
            }));
        }
        start.countDown();
        for (Future<McpSchema.CallToolResult> f : results) {
            McpSchema.CallToolResult r = f.get(60, TimeUnit.SECONDS);
            assertThat(r.isError()).as(text(r)).isFalse();
            assertThat(text(r)).startsWith("Upvoted.").doesNotContain(TOKEN_A).doesNotContain(TOKEN_B);
        }
        pool.shutdown();

        assertThat(upvotes).hasSize(2 * CALLS_PER_CLIENT);
        assertThat(upvotes).allSatisfy(rec -> {
            String expected = rec[0].startsWith("a-") ? "Bearer " + TOKEN_A : "Bearer " + TOKEN_B;
            assertThat(rec[1]).as("auth for post " + rec[0]).isEqualTo(expected);
        });
    }

    @Test
    void noHeader_isReadOnly_andEnvTokenIsIgnored() {
        McpSyncClient anonymous = client(null);

        McpSchema.CallToolResult read = anonymous.callTool(
                new McpSchema.CallToolRequest("get_user_profile", Map.of("username", "carol")));
        assertThat(read.isError()).isFalse();
        assertThat(profileAuth).containsExactly("<none>");

        McpSchema.CallToolResult write = anonymous.callTool(
                new McpSchema.CallToolRequest("upvote_review", Map.of("postId", "x")));
        assertThat(write.isError()).isTrue();
        assertThat(text(write)).isEqualTo("Add your CineCircle token: see Settings → Use CineCircle with Claude");
        assertThat(upvotes).isEmpty();
    }

    // ---- fake backend helpers ----------------------------------------------------------------------------------

    private static String auth(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return h == null ? "<none>" : h;
    }

    private static void respond(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
