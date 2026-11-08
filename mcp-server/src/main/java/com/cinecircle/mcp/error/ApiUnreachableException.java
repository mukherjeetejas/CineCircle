package com.cinecircle.mcp.error;

/** The backend could not be reached at all (connection refused, timeout, DNS ...). */
public class ApiUnreachableException extends RuntimeException {

    private final String apiUrl;

    public ApiUnreachableException(String apiUrl, Throwable cause) {
        super("CineCircle API is not reachable at " + apiUrl, cause);
        this.apiUrl = apiUrl;
    }

    public String apiUrl() {
        return apiUrl;
    }
}
