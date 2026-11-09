package com.cinecircle.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.llm.*}. The API key comes from {@code CINECIRCLE_LLM_API_KEY} (deliberately not
 * {@code ANTHROPIC_API_KEY}); blank key = AI features disabled.
 *
 * @param baseUrl          Anthropic API base URL (only overridden in tests)
 * @param thinkingDisabled send {@code "thinking":{"type":"disabled"}} (default true). Thinking tokens count toward
 *                         {@code max_tokens}, which truncates the small JSON replies. Must be false for models that
 *                         reject disabled thinking (Opus 5.5, Sonnet 5.5, Fable); the field is then omitted.
 * @param dailyCallLimit   max Anthropic calls per UTC day ({@code LLM_DAILY_CALL_LIMIT}, default 150); above it
 *                         {@link LlmClient} behaves as disabled for that call
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        String apiKey,
        @DefaultValue("claude-haiku-5-5") String model,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("true") boolean thinkingDisabled,
        @DefaultValue("150") int dailyCallLimit
) {

    /** Never print the key (records include all components in toString by default). */
    @Override
    public String toString() {
        return "LlmProperties[apiKey=" + (apiKey == null || apiKey.isBlank() ? "<unset>" : "<redacted>")
                + ", model=" + model + ", baseUrl=" + baseUrl + ", thinkingDisabled=" + thinkingDisabled
                + ", dailyCallLimit=" + dailyCallLimit + "]";
    }
}
