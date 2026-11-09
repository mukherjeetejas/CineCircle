package com.cinecircle.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Liveness probe for Render: no auth, no database call. */
@RestController
public class HealthController {

    private static final Map<String, String> OK = Map.of("status", "ok");

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return OK;
    }
}
