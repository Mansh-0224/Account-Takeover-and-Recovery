package com.ato.containment.recovery;

import com.ato.containment.exception.RecoveryException;
import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.incident.IncidentService;
import com.ato.containment.model.EventType;
import com.ato.containment.model.IncidentSeverity;
import com.ato.containment.model.IncidentType;
import com.ato.containment.model.RecoveryRequest;
import com.ato.containment.model.RecoveryStatus;
import com.ato.containment.model.Severity;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RecoveryRequestRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.service.SecurityEventService;
import com.ato.containment.service.SessionService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * The real recovery workflow: request -> verify -> complete. Never trusts a
 * plain "reset my password" call -- every step is tied to one
 * RecoveryRequest row, identified by its own unguessable id (see
 * RecoveryRequest's class comment), checked for status/expiry/attempt count
 * before anything happens.
 */
@Service
public class RecoveryService {

    static final Duration RECOVERY_WINDOW = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RecoveryRequestRepository recoveryRequestRepository;
    private final VerificationProvider verificationProvider;
    private final SessionService sessionService;
    private final SecurityEventService securityEventService;
    private final IncidentService incidentService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public RecoveryService(UserRepository userRepository, RecoveryRequestRepository recoveryRequestRepository,
                           VerificationProvider verificationProvider, SessionService sessionService,
                           SecurityEventService securityEventService, IncidentService incidentService) {
        this.userRepository = userRepository;
        this.recoveryRequestRepository = recoveryRequestRepository;
        this.verificationProvider = verificationProvider;
        this.sessionService = sessionService;
        this.securityEventService = securityEventService;
        this.incidentService = incidentService;
    }

    public record StartResult(RecoveryRequest request, String testModeCode) {
    }

    /**
     * Starts recovery for an email. Returns empty if there is no matching,
     * eligible account -- the caller (RecoveryController) must give the
     * exact same response either way, so this can never be used to discover
     * which emails have accounts.
     */
    public Optional<StartResult> request(String email, String ip, String device) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        Optional<User> match = userRepository.findByEmailIgnoreCase(email);
        if (match.isEmpty()) {
            return Optional.empty();
        }

        User user = match.get();
        if (user.getStatus() == UserStatus.DISABLED) {
            return Optional.empty(); // disabled accounts need an admin, not self-service recovery
        }

        // A newer request always supersedes an older one -- "do not allow
        // older/invalid requests to bypass the newest security state".
        recoveryRequestRepository.findByUserIdAndStatus(user.getId(), RecoveryStatus.PENDING)
                .forEach(old -> {
                    old.setStatus(RecoveryStatus.EXPIRED);
                    recoveryRequestRepository.save(old);
                });

        String rawCode = generateCode();
        RecoveryRequest req = new RecoveryRequest();
        req.setUser(user);
        req.setTenantId(user.getTenant().getId());
        req.setCodeHash(hash(rawCode));
        req.setExpiresAt(Instant.now().plus(RECOVERY_WINDOW));
        recoveryRequestRepository.save(req);

        user.setStatus(UserStatus.RECOVERY_IN_PROGRESS);
        userRepository.save(user);

        VerificationProvider.DeliveryResult delivery = verificationProvider.deliver(user, rawCode);

        securityEventService.record(user.getTenant().getId(), user.getId(), EventType.RECOVERY_STARTED,
                ip, device, Severity.MEDIUM, "Recovery requested.");

        return Optional.of(new StartResult(req, delivery.testModeCode()));
    }

    /** Fetches a recovery request purely by its own id -- see the id's role as the link credential. */
    public RecoveryRequest getPublic(String requestId) {
        return recoveryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("No recovery request found with this id."));
    }

    public RecoveryRequest verify(String requestId, String code, String ip, String device) {
        RecoveryRequest req = getPublic(requestId);

        if (req.getStatus() == RecoveryStatus.COMPLETED) {
            throw new RecoveryException("This recovery request has already been completed.");
        }
        if (req.getStatus() == RecoveryStatus.VERIFIED) {
            return req; // already verified -- re-checking is harmless and idempotent
        }
        if (req.getStatus() == RecoveryStatus.FAILED || req.getStatus() == RecoveryStatus.EXPIRED) {
            throw new RecoveryException("This recovery request is no longer valid. Please request a new one.");
        }
        if (req.isExpired()) {
            req.setStatus(RecoveryStatus.EXPIRED);
            recoveryRequestRepository.save(req);
            logFailed(req, ip, device, "Recovery request expired.");
            throw new RecoveryException("This recovery request has expired.");
        }

        req.setAttemptCounter(req.getAttemptCounter() + 1);
        boolean matches = code != null && hash(code).equals(req.getCodeHash());

        if (!matches) {
            if (req.getAttemptCounter() >= MAX_ATTEMPTS) {
                req.setStatus(RecoveryStatus.FAILED);
                recoveryRequestRepository.save(req);
                logFailed(req, ip, device, "Too many incorrect verification attempts.");
                incidentService.recordAccountTakeoverSignal(req.getTenantId(), req.getUser().getId(),
                        IncidentType.RECOVERY_ABUSE, IncidentSeverity.MEDIUM, 0,
                        "Repeated incorrect recovery verification attempts.");
                throw new RecoveryException("Too many incorrect attempts. Please request a new recovery code.");
            }
            recoveryRequestRepository.save(req);
            logFailed(req, ip, device, "Incorrect verification code.");
            throw new RecoveryException("Incorrect code. " + (MAX_ATTEMPTS - req.getAttemptCounter()) + " attempt(s) remaining.");
        }

        req.setStatus(RecoveryStatus.VERIFIED);
        req.setVerifiedAt(Instant.now());
        return recoveryRequestRepository.save(req);
    }

    public record CompleteResult(String sessionToken, User user) {
    }

    public CompleteResult complete(String requestId, String newPassword, String ip, String device) {
        RecoveryRequest req = getPublic(requestId);

        if (req.getStatus() == RecoveryStatus.COMPLETED) {
            throw new RecoveryException("This recovery request has already been completed.");
        }
        if (req.getStatus() != RecoveryStatus.VERIFIED) {
            throw new RecoveryException("This recovery request has not been verified yet.");
        }
        if (req.isExpired()) {
            req.setStatus(RecoveryStatus.EXPIRED);
            recoveryRequestRepository.save(req);
            logFailed(req, ip, device, "Recovery request expired before completion.");
            throw new RecoveryException("This recovery request has expired.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }

        User user = req.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword)); // same hashing mechanism as everywhere else
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginCount(0);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // Revokes every existing session -- including anything an attacker still held.
        sessionService.revokeAllForUser(user.getId(), null);

        req.setStatus(RecoveryStatus.COMPLETED);
        req.setCompletedAt(Instant.now());
        recoveryRequestRepository.save(req);

        incidentService.markRecoveredForUser(user.getTenant().getId(), user.getId());

        securityEventService.record(user.getTenant().getId(), user.getId(), EventType.RECOVERY_COMPLETED,
                ip, device, Severity.INFO, "Recovery completed; account restored.");

        // A new trusted session, created only now that recovery is genuinely done.
        SessionService.NewSession newSession = sessionService.create(user, device, ip, null, device);
        return new CompleteResult(newSession.rawToken(), user);
    }

    /** Admin oversight view: a user's recovery history, tenant-scoped by the caller. */
    public List<RecoveryRequest> listForTenantAndUser(String tenantId, String userId) {
        return recoveryRequestRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId);
    }

    private void logFailed(RecoveryRequest req, String ip, String device, String reason) {
        securityEventService.record(req.getTenantId(), req.getUser().getId(), EventType.RECOVERY_FAILED,
                ip, device, Severity.MEDIUM, reason);
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
