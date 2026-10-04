package com.ato.containment.model;

/**
 * The catalog of security events this system can log. Not every value here
 * is triggered by a fully-built feature yet — see each value's comment.
 */
public enum EventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    SESSION_CREATED,
    SESSION_REVOKED,
    PASSWORD_CHANGED,

    // Account-recovery module is not built yet (no identity verification,
    // OTP, or credential-reset flow exists). These three values exist so the
    // event schema/API already supports them; nothing in this codebase
    // currently produces them.
    RECOVERY_STARTED,
    RECOVERY_FAILED,
    RECOVERY_COMPLETED,

    // Produced by the risk engine (see risk/RiskEngine) when a login scores HIGH.
    ATO_DETECTED,
    // Produced when a HIGH-risk login or a detected token replay causes the
    // account to be locked (see AuthService, risk/TokenReplayDetector).
    ACCOUNT_CONTAINED,
    // Produced by risk/TokenReplayDetector when the same session is used
    // from a different device shortly after being seen elsewhere.
    TOKEN_REPLAY
}
