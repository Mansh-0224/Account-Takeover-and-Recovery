package com.ato.containment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One risk-scoring result, produced by risk/RiskEngine. {@code signals} is
 * stored as a simple comma-separated list of {@link RiskSignal} names —
 * deliberately not JSON, to keep this entity dependency-free and trivial to
 * read directly out of the database.
 */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel;

    @Column(length = 300)
    private String signals = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private RiskTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskDecision decision;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

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

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getSignals() {
        return signals == null || signals.isBlank() ? List.of() : List.of(signals.split(","));
    }

    public void setSignals(List<RiskSignal> signalList) {
        this.signals = signalList.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
    }

    public RiskTriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(RiskTriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public RiskDecision getDecision() {
        return decision;
    }

    public void setDecision(RiskDecision decision) {
        this.decision = decision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
