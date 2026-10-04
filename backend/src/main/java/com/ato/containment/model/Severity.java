package com.ato.containment.model;

/**
 * How serious a security event is. Matches the badge tiers already used
 * across the frontend prototype (badge-info/low/medium/high/critical in
 * shell.css), so wiring real data into those pages needs no new CSS.
 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
