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
 * A customer company using the platform. Every {@link User} belongs to exactly
 * one tenant, and every tenant-owned record in the system is expected to carry
 * a tenant id so it can never be mixed up with another tenant's data.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Required by JPA/Hibernate; not for application use. */
    protected Tenant() {
    }

    public Tenant(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
