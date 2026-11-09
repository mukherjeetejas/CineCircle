package com.cinecircle.mcp.config;

import com.cinecircle.mcp.client.RetrySleeper;
import com.cinecircle.mcp.tools.MovieTools;
import com.cinecircle.mcp.tools.ReviewTools;
import com.cinecircle.mcp.tools.UserTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
        log.info("CineCircle API: {} · web: {} · env token: {} · rate limit: {}/min per IP",
                props.apiUrl(), props.webUrl(),
                props.token() == null ? "not set" : (props.hasUsableEnvToken() ? "set" : "set but ignored (MCP_ALLOW_ENV_TOKEN=false)"),
                props.rateLimitPerMinute());
        return MethodToolCallbackProvider.builder().toolObjects(movies, reviews, users).build();
    }

    /** Real sleep before the wake-up retry; tests supply their own. */
    @Bean
    @ConditionalOnMissingBean
    public RetrySleeper retrySleeper() {
        return RetrySleeper.THREAD_SLEEP;
    }
}
