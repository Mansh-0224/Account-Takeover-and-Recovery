package com.ato.containment.controller;

import com.ato.containment.exception.RecoveryException;
import com.ato.containment.model.RecoveryRequest;
import com.ato.containment.model.RecoveryStatus;
import com.ato.containment.model.Role;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.recovery.RecoveryService;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecoveryController.class)
class RecoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecoveryService recoveryService;
    @MockitoBean
    private CurrentSessionResolver currentSessionResolver;

    private RecoveryRequest sampleRequest(String tenantId) {
        Tenant tenant = new Tenant("Tenant A", tenantId);
        User user = new User();
        user.setTenant(tenant);
        user.setEmail("person@example.com");
        user.setRole(new Role("USER", "Normal user"));
        RecoveryRequest req = new RecoveryRequest();
        req.setUser(user);
        req.setTenantId(tenantId);
        req.setStatus(RecoveryStatus.PENDING);
        req.setExpiresAt(Instant.now().plusSeconds(900));
        return req;
    }

    @Test
    void request_unknownEmail_returnsGenericResponse_notAnError() throws Exception {
        given(recoveryService.request(any(), any(), any())).willReturn(Optional.empty());

        mockMvc.perform(post("/api/recovery/request").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void request_knownEmail_returnsRequestIdAndTestCode() throws Exception {
        RecoveryRequest req = sampleRequest("tenant-a");
        given(recoveryService.request(any(), any(), any()))
                .willReturn(Optional.of(new RecoveryService.StartResult(req, "123456")));

        mockMvc.perform(post("/api/recovery/request").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "person@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(req.getId()))
                .andExpect(jsonPath("$.testModeCode").value("123456"));
    }

    @Test
    void verify_wrongCode_returnsBadRequest_notAServerError() throws Exception {
        given(recoveryService.verify(eq("req-1"), eq("000000"), any(), any()))
                .willThrow(new RecoveryException("Incorrect code. 4 attempt(s) remaining."));

        mockMvc.perform(post("/api/recovery/req-1/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void complete_onAlreadyCompletedRequest_isRejected() throws Exception {
        given(recoveryService.complete(eq("req-1"), any(), any(), any()))
                .willThrow(new RecoveryException("This recovery request has already been completed."));

        mockMvc.perform(post("/api/recovery/req-1/complete").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "brand-new-password-123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminHistory_isScopedToTheCallersOwnTenant() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willReturn(new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1"));
        given(recoveryService.listForTenantAndUser("tenant-a", "user-1")).willReturn(List.of(sampleRequest("tenant-a")));

        mockMvc.perform(get("/api/recovery/admin").param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void adminHistory_requiresLogin() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willThrow(new com.ato.containment.exception.UnauthorizedException("no session"));

        mockMvc.perform(get("/api/recovery/admin").param("userId", "user-1"))
                .andExpect(status().isUnauthorized());
    }
}
