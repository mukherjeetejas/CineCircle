package com.cinecircle.mcp;

import com.cinecircle.mcp.config.CineCircleProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Context starts with the MCP server auto-configuration and our tool provider. */
@SpringBootTest(properties = "cinecircle.token=secret-test-token")
class CineCircleMcpApplicationTest {

    @Autowired
    ToolCallbackProvider tools;

    @Autowired
    CineCircleProperties props;

    @Test
    void contextLoads_withTenTools() {
        assertThat(tools.getToolCallbacks()).hasSize(10);
        assertThat(Arrays.stream(tools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .contains("search_movies", "create_review");
    }

    @Test
    void propertiesToString_neverShowsToken() {
        assertThat(props.hasToken()).isTrue();
        assertThat(props.toString()).doesNotContain("secret-test-token").contains("<set>");
        assertThat(props.apiUrl()).isEqualTo("http://localhost:8080/api");
    }
}
