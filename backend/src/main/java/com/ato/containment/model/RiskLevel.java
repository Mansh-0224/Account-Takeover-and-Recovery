package com.ato.containment.model;

/**
 * Risk classification bands. Thresholds are the "configured policy" the
 * requirements ask for; see risk/RiskEngine#classify for the actual numbers
 * (kept as named constants there so they're easy to find and change).
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
