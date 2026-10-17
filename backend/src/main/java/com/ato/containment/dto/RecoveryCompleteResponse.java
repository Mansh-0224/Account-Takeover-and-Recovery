package com.ato.containment.dto;

/** A new trusted session, created only after recovery actually completes. */
public record RecoveryCompleteResponse(String sessionToken, UserDto user) {
}
