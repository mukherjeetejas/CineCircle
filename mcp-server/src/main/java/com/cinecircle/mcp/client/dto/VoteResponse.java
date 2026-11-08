package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code {upvoted, upvoteCount}} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VoteResponse(@JsonProperty("upvoted") boolean upvoted, long upvoteCount) {
}
