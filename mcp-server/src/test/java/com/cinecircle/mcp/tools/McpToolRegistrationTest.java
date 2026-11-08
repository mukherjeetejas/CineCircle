package com.cinecircle.mcp.tools;

import com.cinecircle.mcp.config.McpToolsConfig;
import com.cinecircle.mcp.config.CineCircleProperties;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Exercises the tools through the same Spring AI → MCP adapter the server uses (names, schemas, text, isError). */
class McpToolRegistrationTest extends ToolTestSupport {

    private ToolCallback[] callbacks() {
        return new McpToolsConfig()
                .cineCircleTools(movieTools, reviewTools, userTools, new CineCircleProperties(API, null, null))
                .getToolCallbacks();
    }

    private McpSchema.CallToolResult call(String tool, Map<String, Object> args) {
        ToolCallback cb = Arrays.stream(callbacks()).filter(c -> c.getToolDefinition().name().equals(tool))
                .findFirst().orElseThrow();
        return McpToolUtils.toSyncToolSpecification(cb).callHandler()
                .apply(mock(McpSyncServerExchange.class), new McpSchema.CallToolRequest(tool, args));
    }

    private static String text(McpSchema.CallToolResult r) {
        return ((McpSchema.TextContent) r.content().get(0)).text();
    }

    @Test
    void exactlyTheTenSpecifiedTools_withDescriptions() {
        setUp(null);
        assertThat(Arrays.stream(callbacks()).map(c -> c.getToolDefinition().name()))
                .containsExactlyInAnyOrder("search_movies", "get_trending_movies", "get_movie", "get_movie_reviews",
                        "get_review_summary", "get_popular_reviews", "get_user_profile",
                        "create_review", "upvote_review", "comment_on_review");
        assertThat(callbacks()).allSatisfy(c -> assertThat(c.getToolDefinition().description()).hasSizeGreaterThan(60));
    }

    @Test
    void createReviewDescription_tellsToSearchFirst() {
        setUp(null);
        ToolCallback create = Arrays.stream(callbacks())
                .filter(c -> c.getToolDefinition().name().equals("create_review")).findFirst().orElseThrow();
        assertThat(create.getToolDefinition().description()).contains("search_movies").contains("never guess");
    }

    @Test
    void inputSchemas_haveParamNames_andRequiredOnlyWhereNeeded() {
        setUp(null);
        String reviews = Arrays.stream(callbacks()).filter(c -> c.getToolDefinition().name().equals("get_movie_reviews"))
                .findFirst().orElseThrow().getToolDefinition().inputSchema();
        assertThat(reviews).contains("\"tmdbId\"").contains("\"sort\"").contains("\"limit\"").contains("\"includeSpoilers\"")
                .containsPattern("\"required\"\\s*:\\s*\\[\\s*\"tmdbId\"\\s*]");
        String trending = Arrays.stream(callbacks()).filter(c -> c.getToolDefinition().name().equals("get_trending_movies"))
                .findFirst().orElseThrow().getToolDefinition().inputSchema();
        assertThat(trending).doesNotContain("\"required\":[\"");
    }

    @Test
    void successIsPlainText_notJsonEncoded() {
        setUp(null);
        server.expect(requestTo(API + "/movies/trending"))
                .andRespond(withSuccess("[{\"tmdbId\":1,\"title\":\"A\",\"releaseYear\":2020,\"reviewCount\":0}]",
                        MediaType.APPLICATION_JSON));

        McpSchema.CallToolResult r = call("get_trending_movies", Map.of());
        assertThat(r.isError()).isFalse();
        assertThat(text(r)).isEqualTo("Trending movies this week:\n- A (2020) · tmdbId 1 · TMDB ? · no CineCircle reviews yet");
    }

    @Test
    void writeToolWithoutToken_isAnMcpError_withTheExactMessage() {
        setUp(null);
        McpSchema.CallToolResult r = call("create_review",
                Map.of("tmdbId", 438631, "title", "t", "body", "b", "rating", 8));
        assertThat(r.isError()).isTrue();
        assertThat(text(r)).isEqualTo("Set CINECIRCLE_TOKEN to post as your account.");
    }

    @Test
    void optionalParamsMayBeOmitted() {
        setUp(null);
        server.expect(requestTo(API + "/movies/438631/posts?sort=top&page=0&size=10"))
                .andRespond(withSuccess(page(false), MediaType.APPLICATION_JSON));

        McpSchema.CallToolResult r = call("get_movie_reviews", Map.of("tmdbId", 438631));
        assertThat(r.isError()).isFalse();
        assertThat(text(r)).startsWith("No CineCircle reviews");
    }
}
