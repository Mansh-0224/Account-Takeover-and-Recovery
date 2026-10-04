package com.ato.containment.model;

/**
 * One detectable sign that a login (or a request) might not be the real
 * account owner. Each carries the point value the risk engine adds when it
 * fires — see risk/RiskEngine for how signals are combined into a score.
 */
public enum RiskSignal {
    NEW_DEVICE(20, "Login from a device not seen before for this account."),
    UNUSUAL_LOCATION(20, "Login from a location not seen before for this account."),
    MULTIPLE_FAILED_LOGINS(20, "Several failed sign-in attempts shortly before this one succeeded."),
    PASSWORD_CHANGED(30, "Password was changed very recently, shortly before this login."),
    SUSPICIOUS_SESSION(25, "Unusually many sessions already active for this account."),
    TOKEN_REPLAY(50, "The same session token was used from a different device/IP shortly after being seen elsewhere.");

    private final int points;
    private final String description;

    RiskSignal(int points, String description) {
        this.points = points;
        this.description = description;
    }

    public int getPoints() {
        return points;
    }

    public String getDescription() {
        return description;
    }
}
