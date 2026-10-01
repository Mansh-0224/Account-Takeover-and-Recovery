package com.ato.containment.dto;

/**
 * What POST /api/auth/login (and /register) return. {@code sessionToken} is
 * shown to the client exactly once, here — the server never stores it in
 * plain form again (see Session#tokenHash / SessionService).
 */
public record LoginResponse(String sessionToken, UserDto user) {
}
