package com.cinecircle.llm;

/**
 * Today's {@code app.llm.daily-call-limit} is used up: the API was not called and the LLM behaves as disabled for
 * this call. Callers must not count it as a failed attempt (spoiler checks stay PENDING until the next UTC day).
 */
public class LlmCapReachedException extends LlmException {

    public LlmCapReachedException() {
        super("Daily AI call limit reached");
    }
}
