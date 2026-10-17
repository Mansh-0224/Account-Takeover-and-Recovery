package com.ato.containment.incident;

import com.ato.containment.exception.ForbiddenException;
import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.model.Incident;
import com.ato.containment.model.IncidentSeverity;
import com.ato.containment.model.IncidentStatus;
import com.ato.containment.model.IncidentType;
import com.ato.containment.repository.IncidentRepository;
import com.ato.containment.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    private IncidentService incidentService;

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser("admin-1", "tenant-a", "TENANT_ADMIN", "session-1");
    private static final AuthenticatedUser NORMAL_USER = new AuthenticatedUser("user-1", "tenant-a", "USER", "session-2");

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(incidentRepository);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void newSignal_withNoExistingUnresolvedIncident_opensANewOne() {
        when(incidentRepository.findByTenantIdAndUserIdAndStatusIn(eq("tenant-a"), eq("user-9"), any()))
                .thenReturn(List.of());

        Incident incident = incidentService.recordAccountTakeoverSignal(
                "tenant-a", "user-9", IncidentType.ACCOUNT_TAKEOVER, IncidentSeverity.HIGH, 70, "High-risk login.");

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getRiskScore()).isEqualTo(70);
    }

    @Test
    void secondSignal_whileUnresolvedIncidentExists_updatesInsteadOfDuplicating() {
        Incident existing = new Incident();
        existing.setTenantId("tenant-a");
        existing.setUserId("user-9");
        existing.setType(IncidentType.ACCOUNT_TAKEOVER);
        existing.setSeverity(IncidentSeverity.HIGH);
        existing.setRiskScore(65);
        existing.setStatus(IncidentStatus.OPEN);
        existing.setDescription("First signal.");
        when(incidentRepository.findByTenantIdAndUserIdAndStatusIn(eq("tenant-a"), eq("user-9"), any()))
                .thenReturn(List.of(existing));

        Incident result = incidentService.recordAccountTakeoverSignal(
                "tenant-a", "user-9", IncidentType.TOKEN_REPLAY, IncidentSeverity.CRITICAL, 90, "Token replay detected.");

        assertThat(result.getId()).isEqualTo(existing.getId()); // same incident, not a new one
        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL); // bumped to the higher one
        assertThat(result.getRiskScore()).isEqualTo(90);
        assertThat(result.getDescription()).contains("First signal.").contains("Token replay detected.");
    }

    @Test
    void closedIncident_doesNotCountAsUnresolved_soANewOneOpens() {
        // The repository query itself only returns OPEN/INVESTIGATING/CONTAINED,
        // so a CLOSED incident for this user simply isn't in the "existing" list.
        when(incidentRepository.findByTenantIdAndUserIdAndStatusIn(eq("tenant-a"), eq("user-9"), any()))
                .thenReturn(List.of());

        Incident incident = incidentService.recordAccountTakeoverSignal(
                "tenant-a", "user-9", IncidentType.ACCOUNT_TAKEOVER, IncidentSeverity.HIGH, 70, "New incident.");

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void validLifecycleTransitions_succeed() {
        Incident incident = freshIncident();
        when(incidentRepository.findByIdAndTenantId(incident.getId(), "tenant-a")).thenReturn(Optional.of(incident));

        incidentService.investigate(ADMIN, incident.getId());
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);

        incidentService.contain(ADMIN, incident.getId());
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CONTAINED);

        incidentService.moveToRecovery(ADMIN, incident.getId());
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RECOVERED);

        incidentService.close(ADMIN, incident.getId());
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getResolvedAt()).isNotNull();
    }

    @Test
    void skippingAStage_isRejected() {
        Incident incident = freshIncident(); // OPEN
        when(incidentRepository.findByIdAndTenantId(incident.getId(), "tenant-a")).thenReturn(Optional.of(incident));

        // Can't jump straight from OPEN to RECOVERED.
        assertThatThrownBy(() -> incidentService.moveToRecovery(ADMIN, incident.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closedIncident_cannotTransitionFurther() {
        Incident incident = freshIncident();
        incident.setStatus(IncidentStatus.CLOSED);
        when(incidentRepository.findByIdAndTenantId(incident.getId(), "tenant-a")).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.investigate(ADMIN, incident.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalUser_cannotRunLifecycleActions() {
        Incident incident = freshIncident();
        when(incidentRepository.findByIdAndTenantId(incident.getId(), "tenant-a")).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.investigate(NORMAL_USER, incident.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void tenantA_cannotAccessTenantBsIncident() {
        when(incidentRepository.findByIdAndTenantId("incident-in-tenant-b", "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getForTenant("tenant-a", "incident-in-tenant-b"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Incident freshIncident() {
        Incident incident = new Incident();
        incident.setTenantId("tenant-a");
        incident.setUserId("user-9");
        incident.setType(IncidentType.ACCOUNT_TAKEOVER);
        incident.setSeverity(IncidentSeverity.HIGH);
        incident.setRiskScore(70);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setDescription("Test incident.");
        return incident;
    }
}
