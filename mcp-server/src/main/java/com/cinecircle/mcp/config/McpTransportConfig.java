package com.cinecircle.mcp.config;

import com.cinecircle.mcp.auth.HttpRequestInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Hosted mode: makes each MCP HTTP request's {@code Authorization} header and client IP available to the tool call.
 *
 * <p>Spring AI 1.1.8 has no property for the MCP SDK's {@code McpTransportContextExtractor}, but its
 * {@code McpServerStreamableHttpWebMvcAutoConfiguration} declares the transport provider as
 * {@code @ConditionalOnMissingBean}. We provide the same provider (same endpoint {@code /mcp}, same properties)
 * plus {@link HttpRequestInfo#EXTRACTOR}. The SDK runs the extractor for every HTTP request and builds a new
 * {@code McpSyncServerExchange} carrying that request's context, which Spring AI passes to {@code @Tool} methods
 * through {@code ToolContext} (key {@code "exchange"}).
 *
 * <p>Same conditions as the auto-configuration, so the {@code stdio} profile is unaffected.
 */
@Configuration(proxyBeanMethods = false)
@Conditional({McpServerStdioDisabledCondition.class, McpServerAutoConfiguration.EnabledStreamableServerCondition.class})
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
public class McpTransportConfig {

    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            @Qualifier("mcpServerObjectMapper") ObjectMapper objectMapper,
            McpServerStreamableHttpProperties serverProperties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint(serverProperties.getMcpEndpoint())
                .keepAliveInterval(serverProperties.getKeepAliveInterval())
                .disallowDelete(serverProperties.isDisallowDelete())
                .contextExtractor(HttpRequestInfo.EXTRACTOR)
                .build();
    }
}
