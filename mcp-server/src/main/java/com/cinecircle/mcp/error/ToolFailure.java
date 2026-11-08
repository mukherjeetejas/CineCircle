package com.cinecircle.mcp.error;

/**
 * Thrown out of a tool method. Spring AI turns it into an MCP {@code CallToolResult} with {@code isError=true}
 * whose text is exactly {@link #getMessage()}, so the message must be short, readable and safe (no token).
 */
public class ToolFailure extends RuntimeException {

    public ToolFailure(String message) {
        super(message, null, false, false);
    }
}
