package com.ato.containment.controller;

import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import com.ato.containment.service.SecurityEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves security-event search is always scoped to the caller's own tenant
 * (from CurrentSessionResolver), never a client-supplied value -- there is
 * not even a tenantId query parameter to send.
 */
@WebMvcTest(SecurityEventController.class)
class SecurityEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecurityEventService securityEventService;

    @MockitoBean
    private CurrentSessionResolver currentSessionResolver;

    @Test
    void search_usesTheSessionsTenant() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willReturn(new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1"));
        Page<com.ato.containment.model.SecurityEvent> empty = new PageImpl<>(java.util.List.of());
        given(securityEventService.search(eq("tenant-a"), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .willReturn(empty);

        mockMvc.perform(get("/api/security-events")).andExpect(status().isOk());

        verify(securityEventService).search(eq("tenant-a"), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void noValidSession_isRejected() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willThrow(new com.ato.containment.exception.UnauthorizedException("no session"));

        mockMvc.perform(get("/api/security-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSeverityValue_returnsBadRequest_notAServerError() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willReturn(new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1"));

        mockMvc.perform(get("/api/security-events").param("severity", "NOT_A_REAL_SEVERITY"))
                .andExpect(status().isBadRequest());
    }
}
