package com.ato.containment.service;

import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.model.EventType;
import com.ato.containment.model.Session;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Severity;
import com.ato.containment.model.User;
import com.ato.containment.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Owns the whole lifecycle of a login session: creating one at login,
 * resolving a bearer token back to a session on every later request, listing
 * a user's sessions, and revoking them.
 *
 * SECURITY: the raw session token exists in memory only for the moment it is
 * generated in {@link #create}, long enough to be returned to the client once.
 * From then on, only its SHA-256 hash is ever stored or compared — the
 * database never holds anything that could be replayed if it leaked.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final Duration SESSION_LIFETIME = Duration.ofHours(24);

    private final SessionRepository sessionRepository;
    private final SecurityEventService securityEventService;
    private final SecureRandom random = new SecureRandom();

    public SessionService(SessionRepository sessionRepository, SecurityEventService securityEventService) {
        this.sessionRepository = sessionRepository;
        this.securityEventService = securityEventService;
    }

    /** What {@link #create} hands back: the raw token (shown to the client once) plus the stored session row. */
    public record NewSession(String rawToken, Session session) {
    }

    public NewSession create(User user, String deviceId, String ipAddress, String location, String userAgent) {
        String rawToken = generateToken();

        Session session = new Session();
        session.setUser(user);
        session.setTenantId(user.getTenant().getId());
        session.setTokenHash(hash(rawToken));
        session.setDeviceId(deviceId);
        session.setIpAddress(ipAddress);
        session.setLocation(location);
        session.setUserAgent(userAgent);
        session.setLastSeen(Instant.now());
        session.setExpiresAt(Instant.now().plus(SESSION_LIFETIME));
        session.setStatus(SessionStatus.ACTIVE);
        sessionRepository.save(session);

        log.info("Session created: sessionId={} userId={} tenantId={} deviceId={} ip={}",
                session.getId(), user.getId(), user.getTenant().getId(), deviceId, ipAddress);
        securityEventService.record(user.getTenant().getId(), user.getId(), EventType.SESSION_CREATED,
                ipAddress, deviceId, Severity.INFO, "New session created.");

        return new NewSession(rawToken, session);
    }

    /**
     * Looks up the session for a bearer token. Returns empty if the token is
     * unknown, or if the session has been revoked or has expired (an expired
     * session found this way is opportunistically flipped to EXPIRED so the
     * "view active sessions" list reflects reality without needing a
     * separate scheduled job).
     *
     * On every successful resolution, {@code lastSeen} is updated -- this is
     * what lets the sessions list show "active 2 minutes ago".
     */
    public Optional<Session> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<Session> found = sessionRepository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Session session = found.get();
        if (session.getStatus() != SessionStatus.ACTIVE) {
            return Optional.empty();
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(session);
            return Optional.empty();
        }

        session.setLastSeen(Instant.now());
        sessionRepository.save(session);
        return Optional.of(session);
    }

    /** Every session (any status) belonging to this user, most recently active first. */
    public List<Session> listForUser(String userId) {
        return sessionRepository.findByUserIdOrderByLastSeenDesc(userId);
    }

    /**
     * Revokes one session. Scoped to {@code userId} so a user can only revoke
     * their own sessions -- a session id belonging to someone else looks
     * exactly like one that does not exist.
     */
    public void revoke(String userId, String sessionId) {
        Session session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No session found with this id."));
        markRevoked(session);
        log.info("Session revoked: sessionId={} userId={}", sessionId, userId);
    }

    /**
     * Revokes every active session for a user. If {@code exceptSessionId} is
     * given, that one is left alone (used for "log out all OTHER devices" on
     * password change); pass null to revoke everything, including the
     * current session (used for "log out everywhere").
     */
    public void revokeAllForUser(String userId, String exceptSessionId) {
        List<Session> active = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
        for (Session session : active) {
            if (exceptSessionId != null && exceptSessionId.equals(session.getId())) {
                continue;
            }
            markRevoked(session);
        }
        log.info("All sessions revoked for userId={} (except sessionId={})", userId, exceptSessionId);
    }

    private void markRevoked(Session session) {
        session.setStatus(SessionStatus.REVOKED);
        session.setRevokedAt(Instant.now());
        sessionRepository.save(session);
        securityEventService.record(session.getTenantId(), session.getUser().getId(), EventType.SESSION_REVOKED,
                session.getIpAddress(), session.getDeviceId(), Severity.INFO, "Session " + session.getId() + " revoked.");
    }

    private String generateToken() {
        byte[] bytes = new byte[32]; // 256 bits
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a standard algorithm guaranteed to be available on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
