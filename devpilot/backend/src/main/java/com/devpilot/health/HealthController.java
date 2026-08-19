package com.devpilot.health;

import com.devpilot.common.api.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Provides the public liveness endpoint used by development and deployment checks. */
@RestController
@RequestMapping("/api/v1/health")
public final class HealthController {

    private final String applicationName;

    /**
     * Creates the health endpoint.
     *
     * @param applicationName configured Spring application name
     */
    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Reports process liveness without requiring an AI provider or database query.
     *
     * @return current liveness information
     */
    @GetMapping
    public Result<HealthResponse> health() {
        return Result.success(new HealthResponse("UP", applicationName, Instant.now()));
    }

    /**
     * Health payload.
     *
     * @param status liveness status
     * @param application application name
     * @param timestamp response creation time
     */
    public record HealthResponse(String status, String application, Instant timestamp) {
    }
}

