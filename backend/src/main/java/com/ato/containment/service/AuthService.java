package com.ato.containment.service;

import com.ato.containment.exception.UnauthorizedException;
import com.ato.containment.model.EventType;
import com.ato.containment.model.RiskAssessment;
import com.ato.containment.model.RiskDecision;
import com.ato.containment.model.RiskLevel;
import com.ato.containment.model.RiskTriggerType;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Severity;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RiskAssessmentRepository;
import com.ato.containment.repository.SessionRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.risk.RiskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Verifies login attempts (steps 1-5 of the suspicious-login flow: validate
 * credentials and status, collect context, compare with history, score risk,
 * apply the policy), and handles password changes.
 *
 * Does not know about session tokens — AuthController creates the session
 * once this class says a login is allowed.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final RiskEngine riskEngine;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final SecurityEventService securityEventService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, SessionRepository sessionRepository, RiskEngine riskEngine,
                       RiskAssessmentRepository riskAssessmentRepository, SecurityEventService securityEventService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.riskEngine = riskEngine;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.securityEventService = securityEventService;
    }

    /** What a login attempt resolved to. */
    public enum Status {
        SUCCESS,
        INVALID_CREDENTIALS,
        BLOCKED_HIGH_RISK
    }

    public record LoginOutcome(Status status, User user, RiskAssessment riskAssessment) {
        public static LoginOutcome invalid() {
            return new LoginOutcome(Status.INVALID_CREDENTIALS, null, null);
        }
    }

    /**
     * The full suspicious-login flow. Context parameters (device/ip/location)
     * are only ever used to COMPUTE risk, never to decide identity — identity
     * comes solely from the email/password check.
     */
    public LoginOutcome attemptLogin(String email, String rawPassword, String deviceId, String ipAddress, String location) {
        if (email == null || rawPassword == null) {
            return LoginOutcome.invalid();
        }

        Optional<User> match = userRepository.findByEmailIgnoreCase(email);
        if (match.isEmpty()) {
            // No tenant/user to attach this event to -- still worth a record for visibility.
            securityEventService.record(null, null, EventType.LOGIN_FAILURE, ipAddress, deviceId,
                    Severity.LOW, "Login attempt for an email with no matching account.");
            return LoginOutcome.invalid();
        }

        User user = match.get();
        boolean passwordOk = passwordEncoder.matches(rawPassword, user.getPasswordHash());

        if (!passwordOk) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            user.setLastFailedLoginAt(Instant.now());
            userRepository.save(user);
            securityEventService.record(user.getTenant().getId(), user.getId(), EventType.LOGIN_FAILURE,
                    ipAddress, deviceId, Severity.LOW, "Incorrect password.");
            return LoginOutcome.invalid();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            securityEventService.record(user.getTenant().getId(), user.getId(), EventType.LOGIN_FAILURE,
                    ipAddress, deviceId, Severity.MEDIUM, "Login attempt on a non-active account (status: " + user.getStatus() + ").");
            return LoginOutcome.invalid();
        }

        // Credentials are correct and the account is active -- now assess risk.
        RiskEngine.Result risk = riskEngine.assessLogin(user, deviceId, ipAddress, location);

        RiskAssessment assessment = new RiskAssessment();
        assessment.setTenantId(user.getTenant().getId());
        assessment.setUserId(user.getId());
        assessment.setScore(risk.score());
        assessment.setRiskLevel(risk.level());
        assessment.setSignals(List.copyOf(risk.signals()));
        assessment.setTriggerType(RiskTriggerType.LOGIN);
        assessment.setDecision(risk.decision());
        riskAssessmentRepository.save(assessment);

        if (risk.decision() == RiskDecision.BLOCK) {
            // HIGH risk: block the login and contain the account (see docs/03-ato-workflow.md).
            user.setStatus(UserStatus.CONTAINED);
            userRepository.save(user);
            sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)
                    .forEach(s -> {
                        s.setStatus(SessionStatus.REVOKED);
                        s.setRevokedAt(Instant.now());
                        sessionRepository.save(s);
                    });

            securityEventService.record(user.getTenant().getId(), user.getId(), EventType.ATO_DETECTED,
                    ipAddress, deviceId, Severity.HIGH,
                    "High-risk login blocked (score " + risk.score() + "): " + describeSignals(risk) + ".");
            securityEventService.record(user.getTenant().getId(), user.getId(), EventType.ACCOUNT_CONTAINED,
                    ipAddress, deviceId, Severity.HIGH, "Account automatically contained after a high-risk login attempt.");

            return new LoginOutcome(Status.BLOCKED_HIGH_RISK, user, assessment);
        }

        // LOW or MEDIUM: allow the login through either way. MEDIUM is meant
        // to require step-up verification, but no real verification channel
        // (OTP/email challenge) exists in this codebase yet -- rather than
        // pretend to challenge the user, this is allowed through and clearly
        // flagged (MEDIUM severity event + decision=CHALLENGE on the stored
        // assessment) so the gap is honest and visible, not hidden.
        user.setFailedLoginCount(0);
        userRepository.save(user);

        Severity loginSeverity = risk.level() == RiskLevel.MEDIUM ? Severity.MEDIUM : Severity.INFO;
        String description = risk.signals().isEmpty()
                ? "Login succeeded."
                : "Login succeeded with risk score " + risk.score() + " (" + risk.level() + "): " + describeSignals(risk) + ".";
        securityEventService.record(user.getTenant().getId(), user.getId(), EventType.LOGIN_SUCCESS,
                ipAddress, deviceId, loginSeverity, description);

        return new LoginOutcome(Status.SUCCESS, user, assessment);
    }

    private String describeSignals(RiskEngine.Result risk) {
        return risk.signals().isEmpty() ? "no specific signals" :
                String.join(", ", risk.signals().stream().map(Enum::name).toList());
    }

    /**
     * Changes a user's password after verifying their current one. Callers
     * are responsible for deciding what happens to existing sessions
     * afterwards (see AuthController#changePassword, which revokes all
     * other sessions).
     */
    public void changePassword(User user, String currentPassword, String newPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        securityEventService.record(user.getTenant().getId(), user.getId(), EventType.PASSWORD_CHANGED,
                null, null, Severity.MEDIUM, "Password changed.");
        log.info("Password changed. userId={}", user.getId());
    }

    public BCryptPasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}
