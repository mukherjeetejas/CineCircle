package com.cinecircle.mcp.format;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * Returns a tool's String result as-is. (Spring AI's default converter JSON-encodes strings, which would turn
 * our readable multi-line text into one escaped JSON string.)
 */
public class PlainTextResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return result == null ? "" : result.toString();
    }
}
