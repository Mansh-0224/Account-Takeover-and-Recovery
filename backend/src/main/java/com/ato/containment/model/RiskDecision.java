package com.ato.containment.model;

/** What the risk policy decided to do about an assessment. */
public enum RiskDecision {
    ALLOW,
    // MEDIUM risk: the policy wants step-up verification, but no real
    // verification mechanism (OTP/email challenge) exists yet -- see
    // risk/RiskEngine and AuthService for how this is handled honestly.
    CHALLENGE,
    BLOCK
}
