package com.ato.containment.controller;

import com.ato.containment.dto.LoginRequest;
import com.ato.containment.model.Role;
import com.ato.containment.model.RiskAssessment;
import com.ato.containment.model.RiskDecision;
import com.ato.containment.model.RiskLevel;
import com.ato.containment.model.RiskSignal;
import com.ato.containment.model.RiskTriggerType;
import com.ato.containment.model.Session;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RoleRepository;
import com.ato.containment.repository.TenantRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.security.CurrentSessionResolver;
import com.ato.containment.service.AuthService;
import com.ato.containment.service.SessionService;
import com.ato.containment.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the HTTP-level outcome of each risk decision from AuthService:
 * LOW/MEDIUM both return 200 with risk info (MEDIUM flags stepUpRequired),
 * HIGH returns 401 with its own distinct "blocked" message rather than the
 * generic "incorrect email or password" used for bad credentials.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private CurrentSessionResolver currentSessionResolver;
    @MockitoBean
    private TenantRepository tenantRepository;
    @MockitoBean
    private RoleRepository roleRepository;
    @MockitoBean
    private UserRepository userRepository;

    private User sampleUser() {
        Tenant tenant = new Tenant("Tenant A", "tenant-a");
        User user = new User();
        user.setTenant(tenant);
        user.setEmail("person@tenant-a.example");
        user.setFullName("Sample Person");
        user.setRole(new Role("USER", "Normal user"));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private RiskAssessment assessmentWith(RiskLevel level, int score, RiskSignal... signals) {
        RiskAssessment a = new RiskAssessment();
        a.setScore(score);
        a.setRiskLevel(level);
        a.setSignals(List.of(signals));
        a.setTriggerType(RiskTriggerType.LOGIN);
        a.setDecision(level == RiskLevel.HIGH ? RiskDecision.BLOCK : level == RiskLevel.MEDIUM ? RiskDecision.CHALLENGE : RiskDecision.ALLOW);
        return a;
    }

    @Test
    void lowRiskLogin_succeeds_withNoStepUpFlag() throws Exception {
        User user = sampleUser();
        RiskAssessment assessment = assessmentWith(RiskLevel.LOW, 0);
        given(authService.attemptLogin(any(), any(), any(), any(), any()))
                .willReturn(new AuthService.LoginOutcome(AuthService.Status.SUCCESS, user, assessment));
        given(sessionService.create(any(), any(), any(), any(), any()))
                .willReturn(new SessionService.NewSession("raw-token-value", new Session()));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("person@tenant-a.example", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("raw-token-value"))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.stepUpRequired").value(false));
    }

    @Test
    void mediumRiskLogin_succeeds_butFlagsStepUpRequired() throws Exception {
        User user = sampleUser();
        RiskAssessment assessment = assessmentWith(RiskLevel.MEDIUM, 40, RiskSignal.NEW_DEVICE, RiskSignal.UNUSUAL_LOCATION);
        given(authService.attemptLogin(any(), any(), any(), any(), any()))
                .willReturn(new AuthService.LoginOutcome(AuthService.Status.SUCCESS, user, assessment));
        given(sessionService.create(any(), any(), any(), any(), any()))
                .willReturn(new SessionService.NewSession("raw-token-value", new Session()));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("person@tenant-a.example", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.stepUpRequired").value(true))
                .andExpect(jsonPath("$.riskSignals", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void highRiskLogin_isBlocked_withItsOwnDistinctMessage() throws Exception {
        User user = sampleUser();
        RiskAssessment assessment = assessmentWith(RiskLevel.HIGH, 65, RiskSignal.NEW_DEVICE, RiskSignal.MULTIPLE_FAILED_LOGINS);
        given(authService.attemptLogin(any(), any(), any(), any(), any()))
                .willReturn(new AuthService.LoginOutcome(AuthService.Status.BLOCKED_HIGH_RISK, user, assessment));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("person@tenant-a.example", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("temporarily locked")));
    }

    @Test
    void wrongCredentials_getsTheGenericMessage_notTheBlockedOne() throws Exception {
        given(authService.attemptLogin(any(), any(), any(), any(), any()))
                .willReturn(AuthService.LoginOutcome.invalid());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("person@tenant-a.example", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
    }
}
