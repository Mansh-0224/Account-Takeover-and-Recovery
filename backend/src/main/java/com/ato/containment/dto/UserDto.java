package com.ato.containment.dto;

import com.ato.containment.model.User;

import java.time.Instant;

/**
 * What the frontend sees for a user. Deliberately excludes the password hash.
 */
public record UserDto(
        String id,
        String tenantId,
        String tenantName,
        String email,
        String fullName,
        String role,
        String status,
        Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getTenant().getId(),
                user.getTenant().getName(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                user.getStatus().name(),
                user.getCreatedAt());
    }
}
