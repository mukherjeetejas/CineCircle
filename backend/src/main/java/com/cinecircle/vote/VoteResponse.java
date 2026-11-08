package com.cinecircle.vote;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {upvoted, upvoteCount} */
public record VoteResponse(@JsonProperty("upvoted") boolean upvoted, long upvoteCount) {
}
