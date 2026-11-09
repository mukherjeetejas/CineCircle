package com.cinecircle.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** {@code llm_usage}: one document per UTC day counting Anthropic calls (DEPLOY.md daily AI call cap). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("llm_usage")
public class LlmUsage {

    public static final String F_DATE = "date";
    public static final String F_CALLS = "calls";

    @Id
    private String id;

    /** UTC date, {@code yyyy-MM-dd}. */
    @Indexed(unique = true)
    private String date;

    private long calls;
}
