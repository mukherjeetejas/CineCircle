package com.cinecircle.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_OWNER(HttpStatus.FORBIDDEN),
    PRIVATE_ACCOUNT(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 400 when creating a post; controllers may override to 404 for GET /movies/{id}. */
    MOVIE_NOT_FOUND(HttpStatus.BAD_REQUEST),
    USERNAME_TAKEN(HttpStatus.CONFLICT),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    TMDB_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    /** v2: fewer than 3 eligible reviews for an AI summary. */
    NOT_ENOUGH_REVIEWS(HttpStatus.NOT_FOUND),
    /** v2: AI summary could not be generated (LLM disabled, failed, timed out or invalid JSON). */
    SUMMARY_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    /** Unexpected server errors (500). */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus defaultStatus;

    ErrorCode(HttpStatus defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    public HttpStatus defaultStatus() {
        return defaultStatus;
    }
}
