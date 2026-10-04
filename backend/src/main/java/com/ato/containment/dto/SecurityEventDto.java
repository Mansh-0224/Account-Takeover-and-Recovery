package com.ato.containment.dto;

import com.ato.containment.model.SecurityEvent;

import java.time.Instant;

public record SecurityEventDto(
        String eventId,
        String tenantId,
        String userId,
        String eventType,
        Instant timestamp,
        String ip,
        String device,
        String severity,
        String description) {

    public static SecurityEventDto from(SecurityEvent event) {
        return new SecurityEventDto(
                event.getId(),
                event.getTenantId(),
                event.getUserId(),
                event.getEventType().name(),
                event.getTimestamp(),
                event.getIp(),
                event.getDevice(),
                event.getSeverity().name(),
                event.getDescription());
    }
}
