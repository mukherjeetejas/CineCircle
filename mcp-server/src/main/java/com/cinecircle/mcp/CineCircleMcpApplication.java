package com.cinecircle.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CineCircle MCP server. Exposes the CineCircle REST API as MCP tools.
 * <ul>
 *   <li>Default: Streamable HTTP on {@code http://localhost:8090/mcp} (port from {@code PORT}); each request's
 *       {@code Authorization: Bearer} token is used for that call only. {@code GET /health} for health checks.</li>
 *   <li>Profile {@code stdio}: MCP over stdin/stdout for Claude Desktop (no web server, no console logs).</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CineCircleMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineCircleMcpApplication.class, args);
    }
}
