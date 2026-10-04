package com.ato.containment.risk;

import com.ato.containment.model.EventType;
import com.ato.containment.model.RiskAssessment;
import com.ato.containment.model.RiskTriggerType;
import com.ato.containment.model.Session;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Severity;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RiskAssessmentRepository;
import com.ato.containment.repository.SessionRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.service.SecurityEventService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Detects simulated token replay: the SAME session being presented from a
 * different device or IP than it was last seen on, shortly after.
 *
 * ADAPTATION NOTE: this codebase does not issue JWTs, so there is no real
 * {@code jti} claim. {@link Session#getId()} is used as the practical
 * equivalent — it is already a unique per-login identifier, which is exactly
 * what a {@code jti} would be used for here. {@code firstSeen}/{@code lastSeen}
 * are the Session entity's existing {@code createdAt}/{@code lastSeen} fields;
 * no new tracking table was needed.
 *
 * Deliberately conservative: a session with no recorded device/IP yet (e.g.
 * a Postman/curl call with no custom headers), or one that has not been used
 * in a while, is NOT flagged — only a clear device/IP change within the
 * configured recent window counts. This is what keeps normal continued use
 * of a single legitimate session from ever being flagged.
 */
@Component
public class TokenReplayDetector {

    static final Duration REPLAY_WINDOW = Duration.ofMinutes(10);

    private final SessionRepository sessionRepository;
    private final SecurityEventService securityEventService;
    private final RiskEngine riskEngine;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final UserRepository userRepository;

    public TokenReplayDetector(SessionRepository sessionRepository, SecurityEventService securityEventService,
                               RiskEngine riskEngine, RiskAssessmentRepository riskAssessmentRepository,
                               UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.securityEventService = securityEventService;
        this.riskEngine = riskEngine;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Checks one already-resolved, still-ACTIVE session against the device/IP
     * making the CURRENT request. Returns true if this was treated as a
     * replay (in which case the session has already been revoked and the
     * account already contained — the caller should reject this request too).
     */
    public boolean checkAndHandle(Session session, String currentDeviceId, String currentIp) {
        boolean deviceMismatch = currentDeviceId != null && !currentDeviceId.isBlank()
                && session.getDeviceId() != null && !currentDeviceId.equals(session.getDeviceId());
        boolean ipMismatch = currentIp != null && !currentIp.isBlank()
                && session.getIpAddress() != null && !currentIp.equals(session.getIpAddress());

        if (!deviceMismatch && !ipMismatch) {
            return false;
        }

        Instant reference = session.getLastSeen() != null ? session.getLastSeen() : session.getCreatedAt();
        if (reference == null || Duration.between(reference, Instant.now()).compareTo(REPLAY_WINDOW) > 0) {
            return false; // outside the configured window -- not treated as replay here
        }

        handleReplay(session, currentDeviceId, currentIp);
        return true;
    }

    private void handleReplay(Session session, String currentDeviceId, String currentIp) {
        User user = session.getUser();

        // Revoke this exact session immediately -- this is what stops BOTH
        // the attacker and the legitimate owner from continuing to use it.
        session.setStatus(SessionStatus.REVOKED);
        session.setRevokedAt(Instant.now());
        sessionRepository.save(session);

        RiskEngine.Result result = riskEngine.assessTokenReplay();
        RiskAssessment assessment = new RiskAssessment();
        assessment.setTenantId(session.getTenantId());
        assessment.setUserId(user.getId());
        assessment.setScore(result.score());
        assessment.setRiskLevel(result.level());
        assessment.setSignals(List.copyOf(result.signals()));
        assessment.setTriggerType(RiskTriggerType.TOKEN_REPLAY);
        assessment.setDecision(result.decision());
        riskAssessmentRepository.save(assessment);

        securityEventService.record(session.getTenantId(), user.getId(), EventType.TOKEN_REPLAY,
                currentIp, currentDeviceId, Severity.CRITICAL,
                "Session " + session.getId() + " was reused from a different device/IP shortly after being seen elsewhere.");

        // Contain the account: revoke every other active session too, then lock it.
        List<Session> others = sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE);
        for (Session other : others) {
            other.setStatus(SessionStatus.REVOKED);
            other.setRevokedAt(Instant.now());
            sessionRepository.save(other);
        }
        user.setStatus(UserStatus.CONTAINED);
        userRepository.save(user);

        securityEventService.record(session.getTenantId(), user.getId(), EventType.ACCOUNT_CONTAINED,
                currentIp, currentDeviceId, Severity.HIGH,
                "Account automatically contained after a detected token replay.");
    }
}
