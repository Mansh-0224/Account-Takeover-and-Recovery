package com.ato.containment.model;

import java.time.Instant;

/**
 * JSON returned by GET /api/health.
 *
 * @param status    "UP" if everything works, "DEGRADED" if the app runs but the database is unreachable
 * @param service   name of this service
 * @param database  "UP" or "DOWN"
 * @param timestamp when the check was performed (UTC)
 */
public record HealthResponse(String status, String service, String database, Instant timestamp) {
}
