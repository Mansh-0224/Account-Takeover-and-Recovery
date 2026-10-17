package com.ato.containment.controller;

import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.incident.IncidentService;
import com.ato.containment.model.Incident;
import com.ato.containment.model.IncidentSeverity;
import com.ato.containment.model.IncidentStatus;
import com.ato.containment.model.IncidentType;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidentService;

    @MockitoBean
    private CurrentSessionResolver currentSessionResolver;

    private Incident incidentIn(String tenantId) {
        Incident incident = new Incident();
        incident.setTenantId(tenantId);
        incident.setUserId("user-1");
        incident.setType(IncidentType.ACCOUNT_TAKEOVER);
        incident.setSeverity(IncidentSeverity.HIGH);
        incident.setRiskScore(70);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setDescription("Test");
        return incident;
    }

    @Test
    void tenantA_canViewItsOwnIncident() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willReturn(new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1"));
        given(incidentService.getForTenant("tenant-a", "incident-1")).willReturn(incidentIn("tenant-a"));

        mockMvc.perform(get("/api/incidents/incident-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ACCOUNT_TAKEOVER"));
    }

    @Test
    void tenantA_cannotViewTenantBsIncident() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willReturn(new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1"));
        given(incidentService.getForTenant("tenant-a", "incident-in-tenant-b"))
                .willThrow(new ResourceNotFoundException("No incident found with this id."));

        mockMvc.perform(get("/api/incidents/incident-in-tenant-b"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noSession_isRejected() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willThrow(new com.ato.containment.exception.UnauthorizedException("no session"));

        mockMvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized());
    }

    @Test
    void investigateAction_delegatesToService() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willReturn(new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1"));
        Incident incident = incidentIn("tenant-a");
        incident.setStatus(IncidentStatus.INVESTIGATING);
        given(incidentService.investigate(any(), eq("incident-1"))).willReturn(incident);

        mockMvc.perform(post("/api/incidents/incident-1/investigate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));
    }

    private static String eq(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
