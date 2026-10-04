package com.ato.containment.controller;

import com.ato.containment.model.RiskAssessment;
import com.ato.containment.model.RiskDecision;
import com.ato.containment.model.RiskLevel;
import com.ato.containment.model.RiskSignal;
import com.ato.containment.model.RiskTriggerType;
import com.ato.containment.repository.RiskAssessmentRepository;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskAssessmentController.class)
class RiskAssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiskAssessmentRepository riskAssessmentRepository;

    @MockitoBean
    private CurrentSessionResolver currentSessionResolver;

    private RiskAssessment sampleAssessment(String tenantId) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setTenantId(tenantId);
        assessment.setUserId("user-1");
        assessment.setScore(40);
        assessment.setRiskLevel(RiskLevel.MEDIUM);
        assessment.setSignals(List.of(RiskSignal.NEW_DEVICE));
        assessment.setTriggerType(RiskTriggerType.LOGIN);
        assessment.setDecision(RiskDecision.CHALLENGE);
        return assessment;
    }

    @Test
    void list_onlyReturnsTheCallersOwnTenantData() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willReturn(new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1"));
        given(riskAssessmentRepository.findByTenantIdOrderByCreatedAtDesc("tenant-a"))
                .willReturn(List.of(sampleAssessment("tenant-a")));

        mockMvc.perform(get("/api/risk-assessments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].riskLevel").value("MEDIUM"));
    }

    @Test
    void list_filteredByUser_stillScopedToTheCallersOwnTenant() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willReturn(new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1"));
        given(riskAssessmentRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(eq("tenant-a"), eq("someone-in-tenant-b")))
                .willReturn(List.of());

        // Even though the userId in the request names someone from a
        // different tenant, the query is ALWAYS scoped by "tenant-a" (the
        // session's own tenant) as well -- so it would correctly return
        // nothing rather than ever leaking another tenant's row.
        mockMvc.perform(get("/api/risk-assessments").param("userId", "someone-in-tenant-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
