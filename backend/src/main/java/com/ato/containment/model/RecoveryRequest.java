package com.ato.containment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One self-service account-recovery attempt.
 *
 * SECURITY: {@code id} is a UUID and doubles as the recovery link's
 * credential -- the same model this codebase already uses for session
 * tokens being unguessable (see Session), except here the id itself IS what
 * gets handed to the client, because recovery happens before any session
 * exists to authenticate with. Treat a request's id like a bearer token:
 * whoever has it can act on it, so it is never logged or displayed to
 * anyone other than the request's own owner.
 *
 * {@code codeHash} is a SHA-256 hash of the verification code -- the raw
 * code is never stored, same reasoning as a session token or a password.
 */
@Entity
@Table(name = "recovery_requests")
public class RecoveryRequest {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecoveryStatus status = RecoveryStatus.PENDING;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempt_counter", nullable = false)
    private int attemptCounter = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public RecoveryStatus getStatus() {
        return status;
    }

    public void setStatus(RecoveryStatus status) {
        this.status = status;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getAttemptCounter() {
        return attemptCounter;
    }

    public void setAttemptCounter(int attemptCounter) {
        this.attemptCounter = attemptCounter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
