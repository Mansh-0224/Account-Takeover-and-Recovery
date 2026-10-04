package com.ato.containment.service;

import com.ato.containment.model.Role;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RiskAssessmentRepository;
import com.ato.containment.repository.SessionRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.risk.RiskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Covers the full suspicious-login flow: credential/status checks (unchanged
 * from Phase 3) plus the new risk-based decision (LOW allows, MEDIUM allows
 * with a flag, HIGH blocks and contains the account).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // SecurityEventService.record is a side effect, not asserted on every test
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private RiskAssessmentRepository riskAssessmentRepository;
    @Mock
    private SecurityEventService securityEventService;

    private AuthService authService;
    private RiskEngine riskEngine; // real instance, backed by a mocked SessionRepository -- exercises real scoring logic
    private User user;
    private static final String RAW_PASSWORD = "correct horse battery staple";

    @BeforeEach
    void setUp() {
        riskEngine = new RiskEngine(sessionRepository);
        authService = new AuthService(userRepository, sessionRepository, riskEngine, riskAssessmentRepository, securityEventService);

        Tenant tenant = new Tenant("Tenant A", "tenant-a");
        user = new User();
        user.setTenant(tenant);
        user.setEmail("person@tenant-a.example");
        user.setRole(new Role("USER", "Normal user"));
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(new BCryptPasswordEncoder().encode(RAW_PASSWORD));
        user.setPasswordChangedAt(Instant.now().minusSeconds(3600)); // not "recent"
        user.setFailedLoginCount(0);
    }

    private void stubCleanHistory(String deviceId, String ip, String location) {
        if (deviceId != null) when(sessionRepository.existsByUserIdAndDeviceId(user.getId(), deviceId)).thenReturn(true);
        if (location != null) when(sessionRepository.existsByUserIdAndLocation(user.getId(), location)).thenReturn(true);
        when(sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(0L);
    }

    @Test
    void correctPassword_knownContext_lowRisk_succeeds() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        stubCleanHistory("known-device", "1.2.3.4", "London, UK");

        AuthService.LoginOutcome outcome = authService.attemptLogin(user.getEmail(), RAW_PASSWORD, "known-device", "1.2.3.4", "London, UK");

        assertThat(outcome.status()).isEqualTo(AuthService.Status.SUCCESS);
        assertThat(outcome.riskAssessment().getRiskLevel().name()).isEqualTo("LOW");
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    void wrongPassword_isRejectedAndIncrementsFailedCount() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        AuthService.LoginOutcome outcome = authService.attemptLogin(user.getEmail(), "totally-wrong-password", "device", "1.2.3.4", null);

        assertThat(outcome.status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.getLastFailedLoginAt()).isNotNull();
    }

    @Test
    void unknownEmail_isRejected_sameAsWrongPassword() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        AuthService.LoginOutcome outcome = authService.attemptLogin("nobody@example.com", RAW_PASSWORD, "device", "1.2.3.4", null);

        assertThat(outcome.status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
    }

    @Test
    void correctPassword_onADisabledAccount_isRejected() {
        user.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        AuthService.LoginOutcome outcome = authService.attemptLogin(user.getEmail(), RAW_PASSWORD, "device", "1.2.3.4", null);

        assertThat(outcome.status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
    }

    @Test
    void correctPassword_onAContainedAccount_isRejected() {
        user.setStatus(UserStatus.CONTAINED);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        AuthService.LoginOutcome outcome = authService.attemptLogin(user.getEmail(), RAW_PASSWORD, "device", "1.2.3.4", null);

        assertThat(outcome.status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
    }

    @Test
    void mediumRisk_newDeviceAndLocation_stillSucceeds_butFlagged() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(sessionRepository.existsByUserIdAndDeviceId(user.getId(), "new-device")).thenReturn(false);
        when(sessionRepository.existsByUserIdAndLocation(user.getId(), "Lagos, NG")).thenReturn(false);
        when(sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(0L);

        AuthService.LoginOutcome outcome = authService.attemptLogin(user.getEmail(), RAW_PASSWORD, "new-device", "9.9.9.9", "Lagos, NG");

        // No real step-up/OTP verification exists -- MEDIUM is allowed through, not silently treated as LOW.
        assertThat(outcome.status()).isEqualTo(AuthService.Status.SUCCESS);
        assertThat(outcome.riskAssessment().getRiskLevel().name()).isEqualTo("MEDIUM");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE); // not contained
    }

    @Test
    void highRisk_manySignals_isBlockedAndAccountContained() {
        user.setFailedLoginCount(5); // pushes score to HIGH alongside new device/location
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(sessionRepository.existsByUserIdAndDeviceId(user.getId(), "new-device")).thenReturn(false);
        when(sessionRepository.existsByUserIdAndLocation(user.getId(), "Lagos, NG")).thenReturn(false);
        when(sessionRepository.countByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(0L);
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(List.of());

        AuthService.LoginOutcome outcome = authService.attemptLogin(user.getEmail(), RAW_PASSWORD, "new-device", "9.9.9.9", "Lagos, NG");

        assertThat(outcome.status()).isEqualTo(AuthService.Status.BLOCKED_HIGH_RISK);
        assertThat(outcome.riskAssessment().getRiskLevel().name()).isEqualTo("HIGH");
        assertThat(user.getStatus()).isEqualTo(UserStatus.CONTAINED);
    }

    @Test
    void changePassword_withWrongCurrentPassword_isRejected() {
        assertThat(catchException(() -> authService.changePassword(user, "wrong-current", "brand-new-password123")))
                .isNotNull();
    }

    @Test
    void changePassword_withCorrectCurrentPassword_updatesTheHashAndTimestamp() {
        String oldHash = user.getPasswordHash();
        Instant oldChangedAt = user.getPasswordChangedAt();

        authService.changePassword(user, RAW_PASSWORD, "brand-new-password123");

        assertThat(user.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(new BCryptPasswordEncoder().matches("brand-new-password123", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordChangedAt()).isAfter(oldChangedAt);
    }

    private Exception catchException(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
