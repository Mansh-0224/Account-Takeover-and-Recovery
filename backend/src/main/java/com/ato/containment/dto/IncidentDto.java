package com.ato.containment.dto;

import com.ato.containment.model.Incident;

import java.time.Instant;

public record IncidentDto(
        String id,
        String userId,
        String type,
        String severity,
        int riskScore,
        String status,
        Instant createdAt,
        Instant resolvedAt,
        String description) {

    public static IncidentDto from(Incident incident) {
        return new IncidentDto(
                incident.getId(),
                incident.getUserId(),
                incident.getType().name(),
                incident.getSeverity().name(),
                incident.getRiskScore(),
                incident.getStatus().name(),
                incident.getCreatedAt(),
                incident.getResolvedAt(),
                incident.getDescription());
    }
}
