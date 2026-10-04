package com.ato.containment.controller;

import com.ato.containment.dto.RiskAssessmentDto;
import com.ato.containment.repository.RiskAssessmentRepository;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only history of risk assessments, always scoped to the caller's own
 * tenant (from {@link CurrentSessionResolver}, never a request parameter).
 */
@RestController
@RequestMapping("/api/risk-assessments")
public class RiskAssessmentController {

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final CurrentSessionResolver currentSessionResolver;

    public RiskAssessmentController(RiskAssessmentRepository riskAssessmentRepository, CurrentSessionResolver currentSessionResolver) {
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.currentSessionResolver = currentSessionResolver;
    }

    @GetMapping
    public List<RiskAssessmentDto> list(HttpServletRequest request, @RequestParam(required = false) String userId) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        List<com.ato.containment.model.RiskAssessment> results = userId != null
                ? riskAssessmentRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(current.tenantId(), userId)
                : riskAssessmentRepository.findByTenantIdOrderByCreatedAtDesc(current.tenantId());
        return results.stream().map(RiskAssessmentDto::from).toList();
    }
}
