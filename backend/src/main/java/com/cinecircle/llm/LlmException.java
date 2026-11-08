package com.cinecircle.llm;

/**
 * The LLM could not produce a usable answer: disabled, timeout, transport/API error, or a reply that failed
 * validation. Messages never contain the API key or review text.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
