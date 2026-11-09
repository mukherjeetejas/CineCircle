package com.cinecircle.mcp.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** {@code GET /health} → 200 {@code {"status":"ok"}}. No auth, no backend call (used by Render's health check). */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
