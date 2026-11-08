package com.cinecircle.mcp.format;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small text helpers for compact tool output. */
public final class Text {

    /** Review text in lists is the backend's bodyPreview (≤300 chars); clip defensively to the same length. */
    public static final int MAX_PREVIEW = 300;
    public static final int MAX_OVERVIEW = 600;
    public static final int MAX_TITLE = 120;
    public static final int MAX_BIO = 160;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private Text() {
    }

    /** Collapses whitespace to single spaces and cuts to {@code max} characters (with an ellipsis). */
    public static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, max - 1).stripTrailing() + "…";
    }

    public static String date(Instant i) {
        return i == null ? "?" : DATE.format(i);
    }

    public static String dateTime(Instant i) {
        return i == null ? "?" : DATE_TIME.format(i);
    }

    /** One decimal, dot separator, e.g. {@code 7.8}. */
    public static String decimal(Double d) {
        return d == null ? "?" : String.format(Locale.ROOT, "%.1f", d);
    }

    public static String year(Integer y) {
        return y == null ? "" : " (" + y + ")";
    }

    public static String plural(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
