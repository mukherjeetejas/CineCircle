package com.cinecircle.mcp.config;

import com.cinecircle.mcp.tools.MovieTools;
import com.cinecircle.mcp.tools.ReviewTools;
import com.cinecircle.mcp.tools.UserTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the @Tool methods with the Spring AI MCP server. */
@Configuration
public class McpToolsConfig {

    private static final Logger log = LoggerFactory.getLogger(McpToolsConfig.class);

    @Bean
    public ToolCallbackProvider cineCircleTools(MovieTools movies, ReviewTools reviews, UserTools users,
                                                CineCircleProperties props) {
        // Logs whether a token is set, never the token itself.
        log.info("CineCircle API: {} (token {})", props.apiUrl(), props.hasToken() ? "set" : "not set - read-only");
        return MethodToolCallbackProvider.builder().toolObjects(movies, reviews, users).build();
    }
}
