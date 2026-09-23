package com.ato.containment.service;

import com.ato.containment.model.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Checks that the application is running and that MySQL is reachable.
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final int DB_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final String serviceName;

    public HealthService(DataSource dataSource,
                         @Value("${spring.application.name}") String serviceName) {
        this.dataSource = dataSource;
        this.serviceName = serviceName;
    }

    public HealthResponse checkHealth() {
        boolean databaseUp = isDatabaseUp();
        return new HealthResponse(
                databaseUp ? "UP" : "DEGRADED",
                serviceName,
                databaseUp ? "UP" : "DOWN",
                Instant.now());
    }

    private boolean isDatabaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }
}
