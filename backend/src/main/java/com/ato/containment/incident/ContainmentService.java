package com.ato.containment.incident;

import com.ato.containment.model.EventType;
import com.ato.containment.model.Incident;
import com.ato.containment.model.IncidentSeverity;
import com.ato.containment.model.IncidentType;
import com.ato.containment.model.Severity;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.service.SecurityEventService;
import com.ato.containment.service.SessionService;
import org.springframework.stereotype.Service;

/**
 * The one place an account actually gets locked down. AuthService (HIGH-risk
 * login) and risk/TokenReplayDetector (confirmed replay) both call this
 * instead of each doing their own revoke/status/event/incident sequence --
 * that sequence only exists here, once.
 *
 * Flow: revoke sessions (reuses the existing session system) -> mark the
 * account CONTAINED -> log ACCOUNT_CONTAINED -> open or update the
 * corresponding incident.
 */
@Service
public class ContainmentService {

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final SecurityEventService securityEventService;
    private final IncidentService incidentService;

    public ContainmentService(UserRepository userRepository, SessionService sessionService,
                              SecurityEventService securityEventService, IncidentService incidentService) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.securityEventService = securityEventService;
        this.incidentService = incidentService;
    }

    public Incident containAccount(User user, IncidentType reason, IncidentSeverity severity, int riskScore,
                                   String description, String ip, String device) {
        // Reuses the EXISTING session system -- this is what revokes every
        // active session and logs a SESSION_REVOKED event for each one.
        sessionService.revokeAllForUser(user.getId(), null);

        user.setStatus(UserStatus.CONTAINED);
        userRepository.save(user);

        securityEventService.record(user.getTenant().getId(), user.getId(), EventType.ACCOUNT_CONTAINED,
                ip, device, Severity.HIGH, description);

        Incident incident = incidentService.recordAccountTakeoverSignal(
                user.getTenant().getId(), user.getId(), reason, severity, riskScore, description);
        incidentService.markContained(incident);
        return incident;
    }
}
