package com.ato.containment.dto;

import java.util.List;

/**
 * What POST /api/auth/login (and /register) return. {@code sessionToken} is
 * shown to the client exactly once, here — the server never stores it in
 * plain form again (see Session#tokenHash / SessionService).
 *
 * {@code riskLevel}/{@code riskScore}/{@code riskSignals} reflect the login's
 * risk assessment. {@code stepUpRequired} is true only for MEDIUM risk: the
 * policy calls for additional verification there, but no real verification
 * channel (OTP/email challenge) exists in this codebase yet, so the login is
 * still allowed through — this flag exists so the frontend can be honest
 * about that gap rather than silently pretending nothing happened.
 */
public record LoginResponse(
        String sessionToken,
        UserDto user,
        String riskLevel,
        int riskScore,
        List<String> riskSignals,
        boolean stepUpRequired) {
}
