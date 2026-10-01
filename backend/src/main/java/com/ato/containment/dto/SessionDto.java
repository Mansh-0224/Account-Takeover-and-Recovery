package com.ato.containment.dto;

import com.ato.containment.model.Session;

import java.time.Instant;

/**
 * What the frontend sees for a session. No token, hashed or otherwise, is
 * ever included here.
 */
public record SessionDto(
        String sessionId,
        String deviceId,
        String ip,
        String location,
        String userAgent,
        Instant createdAt,
        Instant lastSeen,
        String status,
        Instant revokedAt,
        boolean current) {

    public static SessionDto from(Session session, boolean isCurrent) {
        return new SessionDto(
                session.getId(),
                session.getDeviceId(),
                session.getIpAddress(),
                session.getLocation(),
                session.getUserAgent(),
                session.getCreatedAt(),
                session.getLastSeen(),
                session.getStatus().name(),
                session.getRevokedAt(),
                isCurrent);
    }
}
