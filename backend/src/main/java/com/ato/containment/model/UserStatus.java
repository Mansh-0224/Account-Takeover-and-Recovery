package com.ato.containment.model;

/**
 * The account lifecycle state used for ATO containment/recovery.
 *
 *   ACTIVE              normal, can log in
 *   SUSPICIOUS          flagged by risk detection but not yet contained (e.g. an open, not-yet-investigated incident)
 *   CONTAINED           locked by ContainmentService -- sessions revoked, login blocked
 *   RECOVERY_IN_PROGRESS a recovery request is underway for this account (the "RECOVERY" state)
 *   RECOVERED           recovery just completed; recovery completion sets the account straight to ACTIVE
 *                       in practice (see recovery/RecoveryService), so this value exists mainly for
 *                       display/reporting rather than being a long-lived state
 *   DISABLED            an admin turned this account off; not eligible for self-service recovery
 */
public enum UserStatus {
    ACTIVE,
    SUSPICIOUS,
    CONTAINED,
    RECOVERY_IN_PROGRESS,
    RECOVERED,
    DISABLED
}
