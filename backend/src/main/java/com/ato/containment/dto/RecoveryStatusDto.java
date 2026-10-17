package com.ato.containment.dto;

import com.ato.containment.model.RecoveryRequest;

import java.time.Instant;

public record RecoveryStatusDto(
        String requestId,
        String status,
        Instant expiresAt,
        int attemptCounter,
        Instant createdAt,
        Instant verifiedAt,
        Instant completedAt) {

    public static RecoveryStatusDto from(RecoveryRequest request) {
        return new RecoveryStatusDto(
                request.getId(),
                request.getStatus().name(),
                request.getExpiresAt(),
                request.getAttemptCounter(),
                request.getCreatedAt(),
                request.getVerifiedAt(),
                request.getCompletedAt());
    }
}
