package org.mla.finance.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class StatusController {

    @GetMapping("/api/v1/status")
    public Map<String, Object> status() {
        return Map.of(
            "service", "mla-finance-backend",
            "status", "UP",
            "timestamp", Instant.now().toString()
        );
    }
}
