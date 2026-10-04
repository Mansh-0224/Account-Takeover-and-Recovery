package com.ato.containment.dto;

import com.ato.containment.model.RiskAssessment;

import java.time.Instant;
import java.util.List;

public record RiskAssessmentDto(
        String id,
        String userId,
        int score,
        String riskLevel,
        List<String> signals,
        String triggerType,
        String decision,
        Instant createdAt) {

    public static RiskAssessmentDto from(RiskAssessment assessment) {
        return new RiskAssessmentDto(
                assessment.getId(),
                assessment.getUserId(),
                assessment.getScore(),
                assessment.getRiskLevel().name(),
                assessment.getSignals(),
                assessment.getTriggerType().name(),
                assessment.getDecision().name(),
                assessment.getCreatedAt());
    }
}
