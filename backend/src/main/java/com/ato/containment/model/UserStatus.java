package com.ato.containment.model;

/**
 * The account lifecycle state used for ATO containment/recovery in later phases.
 * Phase 2 only ever sets ACTIVE, but the other values already exist here so the
 * database column doesn't need to change shape when containment/recovery are built.
 */
public enum UserStatus {
    ACTIVE,
    CONTAINED,
    RECOVERY_IN_PROGRESS,
    DISABLED
}
