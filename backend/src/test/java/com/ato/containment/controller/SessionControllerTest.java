package com.ato.containment.controller;

import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import com.ato.containment.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private CurrentSessionResolver currentSessionResolver;

    private AuthenticatedUser asUser(String userId) {
        return new AuthenticatedUser(userId, "tenant-a", "USER", "session-1");
    }

    @Test
    void listingSessions_requiresLogin() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willThrow(new com.ato.containment.exception.UnauthorizedException("no session"));

        mockMvc.perform(get("/api/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    void revokingOwnSession_succeeds() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class))).willReturn(asUser("user-1"));

        mockMvc.perform(delete("/api/sessions/my-session-id"))
                .andExpect(status().isOk());
    }

    @Test
    void revokingSomeoneElsesSession_isDenied() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class))).willReturn(asUser("user-1"));
        willThrow(new ResourceNotFoundException("No session found with this id."))
                .given(sessionService).revoke("user-1", "someone-elses-session-id");

        mockMvc.perform(delete("/api/sessions/someone-elses-session-id"))
                .andExpect(status().isNotFound());
    }
}
