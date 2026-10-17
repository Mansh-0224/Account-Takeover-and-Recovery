package com.ato.containment.incident;

import com.ato.containment.model.EventType;
import com.ato.containment.model.Incident;
import com.ato.containment.model.IncidentSeverity;
import com.ato.containment.model.IncidentStatus;
import com.ato.containment.model.IncidentType;
import com.ato.containment.model.Role;
import com.ato.containment.model.Session;
import com.ato.containment.model.SessionStatus;
import com.ato.containment.model.Severity;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.SessionRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.service.SecurityEventService;
import com.ato.containment.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContainmentServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SecurityEventService securityEventService;
    @Mock
    private IncidentService incidentService;

    private ContainmentService containmentService;
    private User user;

    @BeforeEach
    void setUp() {
        SessionService sessionService = new SessionService(sessionRepository, securityEventService);
        containmentService = new ContainmentService(userRepository, sessionService, securityEventService, incidentService);

        Tenant tenant = new Tenant("Tenant A", "tenant-a");
        user = new User();
        user.setTenant(tenant);
        user.setEmail("person@tenant-a.example");
        user.setRole(new Role("USER", "Normal user"));
        user.setStatus(UserStatus.ACTIVE);
    }

    private Session activeSession() {
        Session session = new Session();
        session.setUser(user);
        session.setTenantId(user.getTenant().getId());
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    @Test
    void containAccount_setsStatusToContained() {
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(List.of());
        Incident incident = new Incident();
        incident.setStatus(IncidentStatus.OPEN);
        when(incidentService.recordAccountTakeoverSignal(any(), any(), any(), any(), anyInt(), any())).thenReturn(incident);

        containmentService.containAccount(user, IncidentType.ACCOUNT_TAKEOVER, IncidentSeverity.HIGH, 75,
                "High-risk login.", "1.2.3.4", "device-1");

        assertThat(user.getStatus()).isEqualTo(UserStatus.CONTAINED);
    }

    @Test
    void containAccount_revokesEveryActiveSession() {
        Session s1 = activeSession();
        Session s2 = activeSession();
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(List.of(s1, s2));
        when(incidentService.recordAccountTakeoverSignal(any(), any(), any(), any(), anyInt(), any())).thenReturn(new Incident());

        containmentService.containAccount(user, IncidentType.ACCOUNT_TAKEOVER, IncidentSeverity.HIGH, 75,
                "High-risk login.", "1.2.3.4", "device-1");

        assertThat(s1.getStatus()).isEqualTo(SessionStatus.REVOKED);
        assertThat(s2.getStatus()).isEqualTo(SessionStatus.REVOKED);
    }

    @Test
    void containAccount_logsSessionRevokedForEachSession_andAccountContained() {
        Session s1 = activeSession();
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(List.of(s1));
        when(incidentService.recordAccountTakeoverSignal(any(), any(), any(), any(), anyInt(), any())).thenReturn(new Incident());

        containmentService.containAccount(user, IncidentType.ACCOUNT_TAKEOVER, IncidentSeverity.HIGH, 75,
                "High-risk login.", "1.2.3.4", "device-1");

        verify(securityEventService).record(eq(user.getTenant().getId()), eq(user.getId()), eq(EventType.SESSION_REVOKED),
                any(), any(), eq(Severity.INFO), any());
        verify(securityEventService).record(eq(user.getTenant().getId()), eq(user.getId()), eq(EventType.ACCOUNT_CONTAINED),
                eq("1.2.3.4"), eq("device-1"), eq(Severity.HIGH), any());
    }

    @Test
    void containAccount_opensOrUpdatesTheIncident_andMarksItContained() {
        when(sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)).thenReturn(List.of());
        Incident incident = new Incident();
        incident.setStatus(IncidentStatus.OPEN);
        when(incidentService.recordAccountTakeoverSignal(user.getTenant().getId(), user.getId(),
                IncidentType.TOKEN_REPLAY, IncidentSeverity.CRITICAL, 90, "Replay detected.")).thenReturn(incident);

        containmentService.containAccount(user, IncidentType.TOKEN_REPLAY, IncidentSeverity.CRITICAL, 90,
                "Replay detected.", "1.2.3.4", "device-1");

        verify(incidentService).markContained(incident);
    }
}
