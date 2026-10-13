package com.example.bugtracker.web;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/readiness probe for the container.
 *
 * <p>Why this exists instead of Actuator: the plan reserves {@code /actuator/health}
 * as a permitted path, but {@code spring-boot-starter-actuator} is not a dependency
 * and adding it just for a health check is not worth the surface area. This endpoint
 * answers the same question with no new dependency.
 *
 * <p>Security: permitted without a token (like {@code /api/auth/login}), because an
 * orchestrator has no user to authenticate as. To keep that safe it deliberately
 * exposes no version, no datasource URL and no error text — only a coarse
 * UP/DOWN/OUT_OF_SERVICE. Detailed errors still come back on the real endpoints,
 * where the {@code @RestControllerAdvice} formats them.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        String db;
        try (Connection connection = dataSource.getConnection()) {
            // 2s timeout: a probe that hangs is worse than a probe that fails.
            db = connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            db = "DOWN";
        }

        String overall = "UP".equals(db) ? "UP" : "OUT_OF_SERVICE";
        Map<String, String> body = Map.of("status", overall, "database", db);

        // 503 when the DB is unreachable, so Docker/compose marks the container
        // unhealthy instead of routing traffic into an API that cannot serve it.
        return "UP".equals(overall)
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(503).body(body);
    }
}