package com.ato.containment.model;

/**
 * Lifecycle state of a login session (see {@link Session}).
 */
public enum SessionStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
