package com.ato.containment.repository;

import com.ato.containment.model.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, String> {

    List<RiskAssessment> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId);

    List<RiskAssessment> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
