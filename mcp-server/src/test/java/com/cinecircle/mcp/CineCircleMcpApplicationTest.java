package com.cinecircle.mcp;

import com.cinecircle.mcp.config.CineCircleProperties;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Context starts with the MCP server auto-configuration, our tool provider and local defaults. */
@SpringBootTest(properties = "cinecircle.token=secret-test-token")
@AutoConfigureMockMvc
class CineCircleMcpApplicationTest {

    @Autowired
    ToolCallbackProvider tools;

    @Autowired
    CineCircleProperties props;

    @Autowired
    ApplicationContext context;

    @Autowired
    Environment env;

    @Autowired
    MockMvc mvc;

    @Test
    void contextLoads_withTenTools() {
        assertThat(tools.getToolCallbacks()).hasSize(10);
        assertThat(Arrays.stream(tools.getToolCallbacks()).map(t -> t.getToolDefinition().name()))
                .contains("search_movies", "create_review");
    }

    @Test
    void propertiesToString_neverShowsToken() {
        assertThat(props.hasUsableEnvToken()).isTrue();
        assertThat(props.toString()).doesNotContain("secret-test-token").contains("<set>");
    }

    @Test
    void localDefaults_needNoEnvVars() {
        assertThat(props.apiUrl()).isEqualTo("http://localhost:8080/api");
        assertThat(props.webUrl()).isEqualTo("http://localhost:5173");
        assertThat(props.allowEnvToken()).isTrue();
        assertThat(props.rateLimitPerMinute()).isEqualTo(60);
        assertThat(props.retryDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(env.getProperty("spring.http.client.read-timeout")).isEqualTo("70s");
        assertThat(env.getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint")).isEqualTo("/mcp");
    }

    @Test
    void ourTransportProvider_replacesTheAutoConfiguredOne() {
        assertThat(context.getBeansOfType(WebMvcStreamableServerTransportProvider.class)).hasSize(1)
                .containsKey("webMvcStreamableServerTransportProvider");
        assertThat(context.getBeanDefinitionNames()).contains("mcpTransportConfig");
    }

    @Test
    void health_isOk_withoutAuth() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }
}
