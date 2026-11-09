package com.cinecircle.mcp.error;

/**
 * The backend still looked asleep after the retry (connect error or a gateway 502/503/504 without the CineCircle
 * error body). Free hosting sleeps when idle; the user should simply try again shortly.
 */
public class BackendWakingUpException extends RuntimeException {

    private final String apiUrl;
    private final String detail;

    /** @param detail short, token-free reason, e.g. "not reachable" or "HTTP 503" */
    public BackendWakingUpException(String apiUrl, String detail, Throwable cause) {
        super("CineCircle is waking up — try again in a minute.", cause);
        this.apiUrl = apiUrl;
        this.detail = detail;
    }

    public String apiUrl() {
        return apiUrl;
    }

    public String detail() {
        return detail;
    }
}
