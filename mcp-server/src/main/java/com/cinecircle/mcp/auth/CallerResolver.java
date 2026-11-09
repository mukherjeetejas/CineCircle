package com.cinecircle.mcp.auth;

import com.cinecircle.mcp.config.CineCircleProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.stereotype.Component;

/**
 * Works out the {@link Caller} of one tool call from the Spring AI {@link ToolContext}.
 * <ul>
 *   <li>HTTP call (our transport context present): the token from THAT request's {@code Authorization: Bearer} header.
 *       Without a header, {@code CINECIRCLE_TOKEN} is used only if {@code MCP_ALLOW_ENV_TOKEN=true} (local use);
 *       otherwise the caller is read-only.</li>
 *   <li>stdio / no transport context: {@code CINECIRCLE_TOKEN} if allowed, else read-only.</li>
 * </ul>
 * Stateless: nothing about a caller is remembered between calls.
 */
@Component
public class CallerResolver {

    private final CineCircleProperties props;

    public CallerResolver(CineCircleProperties props) {
        this.props = props;
    }

    public Caller resolve(ToolContext toolContext) {
        HttpRequestInfo http = httpInfo(toolContext);
        if (http != null) {
            String token = http.bearerToken() != null ? http.bearerToken() : props.usableEnvToken();
            return new Caller(token, http.clientIp(), true);
        }
        return Caller.local(props.usableEnvToken());
    }

    private static HttpRequestInfo httpInfo(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object exchangeOrContext = toolContext.getContext().get(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY);
        McpTransportContext transport = null;
        if (exchangeOrContext instanceof McpSyncServerExchange exchange) {
            transport = exchange.transportContext();
        } else if (exchangeOrContext instanceof McpTransportContext ctx) { // stateless servers pass the context itself
            transport = ctx;
        }
        if (transport == null) {
            return null;
        }
        return transport.get(HttpRequestInfo.CONTEXT_KEY) instanceof HttpRequestInfo info ? info : null;
    }
}
