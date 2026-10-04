package com.ato.containment.risk;

import com.ato.containment.model.RiskDecision;
import com.ato.containment.model.RiskLevel;
import com.ato.containment.model.RiskSignal;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.User;
import com.ato.containment.repository.SessionRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Rule-based (not ML) risk scoring. Each rule either fires once or not at
 * all for a given assessment -- using a {@link Set} makes "don't add the
 * same signal twice" structurally impossible to get wrong, on top of each
 * rule already only being evaluated once per call.
 *
 * Thresholds and the failed-login/session-count cutoffs are named constants
 * here specifically so they're easy to find and change -- that is what
 * "configurable" means in this implementation (no separate config store or
 * admin UI for it yet).
 */
@Component
public class RiskEngine {

    // ---- Policy thresholds (risk level) ----
    static final int MEDIUM_THRESHOLD = 30; // score >= this -> at least MEDIUM
    static final int HIGH_THRESHOLD = 60;   // score >= this -> HIGH

    // ---- Rule cutoffs ----
    static final int FAILED_LOGIN_THRESHOLD = 3;       // this many recent failures -> MULTIPLE_FAILED_LOGINS
    static final Duration RECENT_PASSWORD_CHANGE_WINDOW = Duration.ofMinutes(10);
    static final int SUSPICIOUS_SESSION_COUNT_THRESHOLD = 3; // this many other ACTIVE sessions -> SUSPICIOUS_SESSION

    private final SessionRepository sessionRepository;

    public RiskEngine(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public record Result(int score, RiskLevel level, Set<RiskSignal> signals, RiskDecision decision) {
    }

    /**
     * Scores a login attempt for a user whose credentials have already been
     * verified (this never runs for a failed password/unknown email -- see
     * AuthService).
     */
    public Result assessLogin(User user, String deviceId, String ipAddress, String location) {
        Set<RiskSignal> signals = new LinkedHashSet<>();

        if (deviceId != null && !deviceId.isBlank()
                && !sessionRepository.existsByUserIdAndDeviceId(user.getId(), deviceId)) {
            signals.add(RiskSignal.NEW_DEVICE);
        }

        if (location != null && !location.isBlank()
                && !sessionRepository.existsByUserIdAndLocation(user.getId(), location)) {
            signals.add(RiskSignal.UNUSUAL_LOCATION);
        }

        if (user.getFailedLoginCount() >= FAILED_LOGIN_THRESHOLD) {
            signals.add(RiskSignal.MULTIPLE_FAILED_LOGINS);
        }

        if (user.getPasswordChangedAt() != null
                && Duration.between(user.getPasswordChangedAt(), Instant.now()).compareTo(RECENT_PASSWORD_CHANGE_WINDOW) <= 0) {
            signals.add(RiskSignal.PASSWORD_CHANGED);
        }

        long activeSessions = sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE);
        if (activeSessions >= SUSPICIOUS_SESSION_COUNT_THRESHOLD) {
            signals.add(RiskSignal.SUSPICIOUS_SESSION);
        }

        return score(signals);
    }

    /**
     * Scores a detected token replay in isolation (no other login signals
     * apply -- this happens mid-session, not at login). See
     * risk/TokenReplayDetector.
     */
    public Result assessTokenReplay() {
        return score(Set.of(RiskSignal.TOKEN_REPLAY));
    }

    private Result score(Set<RiskSignal> signals) {
        int total = signals.stream().mapToInt(RiskSignal::getPoints).sum();
        RiskLevel level = classify(total);
        RiskDecision decision = switch (level) {
            case LOW -> RiskDecision.ALLOW;
            case MEDIUM -> RiskDecision.CHALLENGE;
            case HIGH -> RiskDecision.BLOCK;
        };
        return new Result(total, level, signals, decision);
    }

    static RiskLevel classify(int score) {
        if (score >= HIGH_THRESHOLD) return RiskLevel.HIGH;
        if (score >= MEDIUM_THRESHOLD) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}
