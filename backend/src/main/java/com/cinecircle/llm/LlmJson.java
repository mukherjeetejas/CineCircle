package com.cinecircle.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Helpers for turning a model reply into a JSON object. */
public final class LlmJson {

    private LlmJson() {
    }

    /**
     * Parses the reply as a JSON object. Tolerates surrounding whitespace, a Markdown code fence or a short
     * preamble by taking the outermost {@code {...}}.
     *
     * @throws LlmException if no JSON object can be parsed
     */
    public static JsonNode parseObject(ObjectMapper mapper, String text) {
        if (text == null) {
            throw new LlmException("Empty LLM reply");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException("LLM reply is not a JSON object");
        }
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            if (node == null || !node.isObject()) {
                throw new LlmException("LLM reply is not a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new LlmException("LLM reply is not valid JSON");
        }
    }
}
