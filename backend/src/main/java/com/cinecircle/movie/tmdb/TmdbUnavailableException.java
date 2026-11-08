package com.cinecircle.movie.tmdb;

import com.cinecircle.common.ApiException;
import com.cinecircle.common.ErrorCode;

/** TMDB timed out, refused, or returned an unexpected error: 502 TMDB_UNAVAILABLE. */
public class TmdbUnavailableException extends ApiException {

    public TmdbUnavailableException(String message) {
        super(ErrorCode.TMDB_UNAVAILABLE, message);
    }
}
