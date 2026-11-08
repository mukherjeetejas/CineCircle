package com.cinecircle.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CineCircle MCP server. Exposes the CineCircle REST API as MCP tools.
 * <ul>
 *   <li>Default: Streamable HTTP on {@code http://localhost:8090/mcp}.</li>
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
