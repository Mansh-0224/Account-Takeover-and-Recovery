package com.ato.containment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One row in the centralized security audit log. Rows are append-only —
 * nothing in this codebase updates or deletes a SecurityEvent after creation.
 *
 * SECURITY: {@code description} must never contain a password, OTP, session
 * token, or Authorization header value. Every call site that builds one of
 * these (see SecurityEventService#record) is responsible for only passing
 * safe, already-redacted text. userId/tenantId are plain ids, not secrets.
 */
@Entity
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    // Nullable: a LOGIN_FAILURE for an email that doesn't exist at all has no
    // tenant to attach to. Every OTHER event always has one.
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    // Nullable for the same reason as tenantId.
    @Column(name = "user_id", length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "ip_address", length = 45)
    private String ip;

    @Column(length = 300)
    private String device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(length = 500)
    private String description;

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
