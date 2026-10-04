package com.ato.containment.repository;

import com.ato.containment.model.Session;
import com.ato.containment.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, String> {

    Optional<Session> findByTokenHash(String tokenHash);

    List<Session> findByUserIdOrderByLastSeenDesc(String userId);

    /**
     * The only way a session should ever be fetched for revocation: scoped to
     * the user who owns it. A session id that belongs to someone else's
     * account is simply not found — same "looks nonexistent" pattern used
     * for cross-tenant user lookups.
     */
    Optional<Session> findByIdAndUserId(String id, String userId);

    List<Session> findByUserIdAndStatus(String userId, SessionStatus status);

    // Used by risk/RiskEngine's NEW_DEVICE / UNUSUAL_LOCATION signals: "has
    // this user ever had a session with this device/location before?"
    boolean existsByUserIdAndDeviceId(String userId, String deviceId);

    boolean existsByUserIdAndLocation(String userId, String location);

    long countByUserIdAndStatus(String userId, SessionStatus status);
}
