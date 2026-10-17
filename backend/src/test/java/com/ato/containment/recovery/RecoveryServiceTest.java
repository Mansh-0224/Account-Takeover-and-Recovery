package com.ato.containment.recovery;

import com.ato.containment.exception.RecoveryException;
import com.ato.containment.incident.IncidentService;
import com.ato.containment.model.IncidentSeverity;
import com.ato.containment.model.IncidentType;
import com.ato.containment.model.RecoveryRequest;
import com.ato.containment.model.RecoveryStatus;
import com.ato.containment.model.Role;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RecoveryRequestRepository;
import com.ato.containment.repository.SessionRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.service.SecurityEventService;
import com.ato.containment.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecoveryServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RecoveryRequestRepository recoveryRequestRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SecurityEventService securityEventService;
    @Mock
    private IncidentService incidentService;

    private RecoveryService recoveryService;
    private VerificationProvider capturingProvider;
    private final Map<String, String> savedRows = new HashMap<>();
    private User user;
    private String capturedCode;

    @BeforeEach
    void setUp() {
        SessionService sessionService = new SessionService(sessionRepository, securityEventService);
        capturingProvider = (u, rawCode) -> {
            capturedCode = rawCode;
            return new VerificationProvider.DeliveryResult(true, rawCode);
        };
        recoveryService = new RecoveryService(userRepository, recoveryRequestRepository, capturingProvider,
                sessionService, securityEventService, incidentService);

        Tenant tenant = new Tenant("Tenant A", "tenant-a");
        user = new User();
        user.setTenant(tenant);
        user.setEmail("person@tenant-a.example");
        user.setRole(new Role("USER", "Normal user"));
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("old-password-123"));

        // A simple in-memory "database" for RecoveryRequest so findById/save behave consistently across calls.
        when(recoveryRequestRepository.save(any(RecoveryRequest.class))).thenAnswer(inv -> {
            RecoveryRequest r = inv.getArgument(0);
            savedRows.put(r.getId(), r.getId());
            return r;
        });
    }

    private RecoveryRequest startRequest() {
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(recoveryRequestRepository.findByUserIdAndStatus(user.getId(), RecoveryStatus.PENDING)).thenReturn(List.of());
        RecoveryService.StartResult result = recoveryService.request(user.getEmail(), "1.2.3.4", "device-1").orElseThrow();
        when(recoveryRequestRepository.findById(result.request().getId())).thenReturn(Optional.of(result.request()));
        return result.request();
    }

    @Test
    void request_unknownEmail_returnsEmpty_noEnumeration() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThat(recoveryService.request("nobody@example.com", "1.2.3.4", "device-1")).isEmpty();
    }

    @Test
    void request_disabledAccount_returnsEmpty() {
        user.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        assertThat(recoveryService.request(user.getEmail(), "1.2.3.4", "device-1")).isEmpty();
    }

    @Test
    void request_valid_createsPendingRequest() {
        RecoveryRequest req = startRequest();

        assertThat(req.getStatus()).isEqualTo(RecoveryStatus.PENDING);
        assertThat(req.isExpired()).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.RECOVERY_IN_PROGRESS);
    }

    @Test
    void request_newOne_supersedesOlderPendingRequest() {
        RecoveryRequest older = new RecoveryRequest();
        older.setUser(user);
        older.setStatus(RecoveryStatus.PENDING);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(recoveryRequestRepository.findByUserIdAndStatus(user.getId(), RecoveryStatus.PENDING)).thenReturn(List.of(older));

        recoveryService.request(user.getEmail(), "1.2.3.4", "device-1");

        assertThat(older.getStatus()).isEqualTo(RecoveryStatus.EXPIRED);
    }

    @Test
    void verify_correctCode_movesToVerified() {
        RecoveryRequest req = startRequest();

        RecoveryRequest verified = recoveryService.verify(req.getId(), capturedCode, "1.2.3.4", "device-1");

        assertThat(verified.getStatus()).isEqualTo(RecoveryStatus.VERIFIED);
        assertThat(verified.getVerifiedAt()).isNotNull();
    }

    @Test
    void verify_wrongCode_isRejected_andIncrementsAttemptCounter() {
        RecoveryRequest req = startRequest();

        assertThatThrownBy(() -> recoveryService.verify(req.getId(), "000000", "1.2.3.4", "device-1"))
                .isInstanceOf(RecoveryException.class);

        assertThat(req.getStatus()).isEqualTo(RecoveryStatus.PENDING); // still retryable
        assertThat(req.getAttemptCounter()).isEqualTo(1);
    }

    @Test
    void verify_tooManyWrongAttempts_marksFailed_andOpensAbuseIncident() {
        RecoveryRequest req = startRequest();
        String wrongCode = capturedCode.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < RecoveryService.MAX_ATTEMPTS - 1; i++) {
            assertThatThrownBy(() -> recoveryService.verify(req.getId(), wrongCode, "1.2.3.4", "device-1"));
        }
        assertThatThrownBy(() -> recoveryService.verify(req.getId(), wrongCode, "1.2.3.4", "device-1"))
                .isInstanceOf(RecoveryException.class);

        assertThat(req.getStatus()).isEqualTo(RecoveryStatus.FAILED);
        verify(incidentService).recordAccountTakeoverSignal(any(), any(), eq(IncidentType.RECOVERY_ABUSE), any(), anyInt(), anyString());
    }

    @Test
    void verify_onExpiredRequest_isRejected_andMarksExpired() {
        RecoveryRequest req = startRequest();
        req.setExpiresAt(Instant.now().minusSeconds(60)); // force expiry

        assertThatThrownBy(() -> recoveryService.verify(req.getId(), capturedCode, "1.2.3.4", "device-1"))
                .isInstanceOf(RecoveryException.class);
        assertThat(req.getStatus()).isEqualTo(RecoveryStatus.EXPIRED);
    }

    @Test
    void verify_onAlreadyCompletedRequest_isRejected() {
        RecoveryRequest req = startRequest();
        req.setStatus(RecoveryStatus.COMPLETED);

        assertThatThrownBy(() -> recoveryService.verify(req.getId(), capturedCode, "1.2.3.4", "device-1"))
                .isInstanceOf(RecoveryException.class);
    }

    @Test
    void complete_withoutVerification_isRejected() {
        RecoveryRequest req = startRequest(); // still PENDING

        assertThatThrownBy(() -> recoveryService.complete(req.getId(), "brand-new-password-123", "1.2.3.4", "device-1"))
                .isInstanceOf(RecoveryException.class);
    }

    @Test
    void complete_afterVerification_succeeds_andCreatesNewSession() {
        RecoveryRequest req = startRequest();
        recoveryService.verify(req.getId(), capturedCode, "1.2.3.4", "device-1");
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(new ArrayList<>());

        RecoveryService.CompleteResult result = recoveryService.complete(req.getId(), "brand-new-password-123", "1.2.3.4", "device-1");

        assertThat(result.sessionToken()).isNotBlank();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(req.getStatus()).isEqualTo(RecoveryStatus.COMPLETED);
        assertThat(new BCryptPasswordEncoder().matches("brand-new-password-123", user.getPasswordHash())).isTrue();
    }

    @Test
    void complete_revokesAllExistingSessions() {
        RecoveryRequest req = startRequest();
        recoveryService.verify(req.getId(), capturedCode, "1.2.3.4", "device-1");
        com.ato.containment.model.Session leftoverSession = new com.ato.containment.model.Session();
        leftoverSession.setUser(user);
        leftoverSession.setTenantId(user.getTenant().getId());
        leftoverSession.setStatus(SessionStatus.ACTIVE);
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE))
                .thenReturn(new ArrayList<>(List.of(leftoverSession)));

        recoveryService.complete(req.getId(), "brand-new-password-123", "1.2.3.4", "device-1");

        assertThat(leftoverSession.getStatus()).isEqualTo(SessionStatus.REVOKED);
    }

    @Test
    void complete_onAlreadyCompletedRequest_isRejected_cannotReplay() {
        RecoveryRequest req = startRequest();
        recoveryService.verify(req.getId(), capturedCode, "1.2.3.4", "device-1");
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(new ArrayList<>());
        recoveryService.complete(req.getId(), "brand-new-password-123", "1.2.3.4", "device-1");

        // Try to complete the SAME request again with a different password.
        assertThatThrownBy(() -> recoveryService.complete(req.getId(), "another-password-456", "1.2.3.4", "device-1"))
                .isInstanceOf(RecoveryException.class);
    }

    @Test
    void complete_withShortPassword_isRejected() {
        RecoveryRequest req = startRequest();
        recoveryService.verify(req.getId(), capturedCode, "1.2.3.4", "device-1");

        assertThatThrownBy(() -> recoveryService.complete(req.getId(), "short", "1.2.3.4", "device-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
