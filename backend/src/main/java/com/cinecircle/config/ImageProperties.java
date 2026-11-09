package com.cinecircle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.images.*} (DEPLOY.md storage guard).
 *
 * @param maxBytes       largest accepted upload ({@code IMAGE_MAX_BYTES}); larger -> 400 VALIDATION_ERROR
 * @param storageLimitMb total size of the GridFS {@code images} bucket at which uploads are refused with
 *                       507 STORAGE_FULL ({@code IMAGE_STORAGE_LIMIT_MB})
 */
@ConfigurationProperties(prefix = "app.images")
public record ImageProperties(
        @DefaultValue("5242880") long maxBytes,
        @DefaultValue("300") long storageLimitMb
) {

    public long storageLimitBytes() {
        return storageLimitMb * 1024L * 1024L;
    }

    /** "Image must be under 5 MB" style message (whole MB when exact, otherwise one decimal). */
    public String tooLargeMessage() {
        double mb = maxBytes / (1024.0 * 1024.0);
        String size = mb == Math.rint(mb) ? String.valueOf((long) mb)
                : String.format(java.util.Locale.ROOT, "%.1f", mb);
        return "Image must be under " + size + " MB";
    }
}
