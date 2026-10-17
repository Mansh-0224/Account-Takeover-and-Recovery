package com.ato.containment.model;

/**
 * PENDING -> VERIFIED -> COMPLETED is the happy path.
 * FAILED: too many wrong verification attempts.
 * EXPIRED: the window passed, or a newer request for the same user superseded it.
 * Once COMPLETED, FAILED, or EXPIRED, a request is terminal -- see
 * recovery/RecoveryService, which never moves a request backward.
 */
public enum RecoveryStatus {
    PENDING,
    VERIFIED,
    COMPLETED,
    FAILED,
    EXPIRED
}
