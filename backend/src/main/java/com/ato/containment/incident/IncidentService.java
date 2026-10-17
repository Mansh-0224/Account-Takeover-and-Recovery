package com.ato.containment.incident;

import com.ato.containment.exception.ForbiddenException;
import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.model.Incident;
import com.ato.containment.model.IncidentSeverity;
import com.ato.containment.model.IncidentStatus;
import com.ato.containment.model.IncidentType;
import com.ato.containment.repository.IncidentRepository;
import com.ato.containment.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Incident reads/writes, scoped to the caller's tenant exactly like
 * UserService does for users. {@link #recordAccountTakeoverSignal} is the
 * entry point ContainmentService uses whenever risk detection finds
 * something serious -- it reuses an existing unresolved incident for the
 * same user instead of piling up duplicates.
 */
@Service
public class IncidentService {

    // Only these statuses count as "still open" for dedup purposes.
    private static final List<IncidentStatus> UNRESOLVED = List.of(
            IncidentStatus.OPEN, IncidentStatus.INVESTIGATING, IncidentStatus.CONTAINED);

    // The only moves an admin action (or the system) is allowed to make.
    private static final Map<IncidentStatus, Set<IncidentStatus>> VALID_TRANSITIONS = Map.of(
            IncidentStatus.OPEN, Set.of(IncidentStatus.INVESTIGATING, IncidentStatus.CONTAINED),
            IncidentStatus.INVESTIGATING, Set.of(IncidentStatus.CONTAINED),
            IncidentStatus.CONTAINED, Set.of(IncidentStatus.RECOVERED),
            IncidentStatus.RECOVERED, Set.of(IncidentStatus.CLOSED),
            IncidentStatus.CLOSED, Set.of());

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public List<Incident> listForTenant(String tenantId) {
        return incidentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public Incident getForTenant(String tenantId, String id) {
        return incidentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No incident found with this id."));
    }

    /**
     * Opens a new ACCOUNT_TAKEOVER-style incident, or -- if this user already
     * has an unresolved one -- folds the new signal into it instead (keeps
     * the higher severity/score, appends to the description). This is what
     * "avoid duplicate active ATO incidents for the same user" means here.
     */
    public Incident recordAccountTakeoverSignal(String tenantId, String userId, IncidentType type,
                                                IncidentSeverity severity, int riskScore, String description) {
        List<Incident> unresolved = incidentRepository.findByTenantIdAndUserIdAndStatusIn(tenantId, userId, UNRESOLVED);

        if (!unresolved.isEmpty()) {
            Incident existing = unresolved.get(0);
            if (riskScore > existing.getRiskScore()) {
                existing.setRiskScore(riskScore);
            }
            if (severity.ordinal() > existing.getSeverity().ordinal()) {
                existing.setSeverity(severity);
            }
            existing.setDescription(existing.getDescription() + " | " + description);
            return incidentRepository.save(existing);
        }

        Incident incident = new Incident();
        incident.setTenantId(tenantId);
        incident.setUserId(userId);
        incident.setType(type);
        incident.setSeverity(severity);
        incident.setRiskScore(riskScore);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setDescription(description);
        return incidentRepository.save(incident);
    }

    /**
     * System-triggered move straight to CONTAINED, used right after
     * ContainmentService locks an account. Not an admin HTTP action, so it
     * skips the role check -- but still only moves forward along the same
     * valid-transition rules (a CLOSED incident never gets silently reopened).
     */
    public void markContained(Incident incident) {
        if (VALID_TRANSITIONS.getOrDefault(incident.getStatus(), Set.of()).contains(IncidentStatus.CONTAINED)
                || incident.getStatus() == IncidentStatus.CONTAINED) {
            incident.setStatus(IncidentStatus.CONTAINED);
            incidentRepository.save(incident);
        }
    }

    /** Marks any still-open incident for this user as RECOVERED, called when their recovery completes. */
    public void markRecoveredForUser(String tenantId, String userId) {
        incidentRepository.findByTenantIdAndUserIdAndStatusIn(tenantId, userId, List.of(IncidentStatus.CONTAINED))
                .forEach(incident -> {
                    incident.setStatus(IncidentStatus.RECOVERED);
                    incidentRepository.save(incident);
                });
    }

    public Incident investigate(AuthenticatedUser actor, String id) {
        return transition(actor, id, IncidentStatus.INVESTIGATING);
    }

    public Incident contain(AuthenticatedUser actor, String id) {
        return transition(actor, id, IncidentStatus.CONTAINED);
    }

    public Incident moveToRecovery(AuthenticatedUser actor, String id) {
        return transition(actor, id, IncidentStatus.RECOVERED);
    }

    public Incident close(AuthenticatedUser actor, String id) {
        return transition(actor, id, IncidentStatus.CLOSED);
    }

    private Incident transition(AuthenticatedUser actor, String id, IncidentStatus target) {
        requireAdmin(actor);
        Incident incident = getForTenant(actor.tenantId(), id);

        if (!VALID_TRANSITIONS.getOrDefault(incident.getStatus(), Set.of()).contains(target)) {
            throw new IllegalArgumentException(
                    "Cannot move an incident from " + incident.getStatus() + " to " + target + ".");
        }

        incident.setStatus(target);
        if (target == IncidentStatus.CLOSED) {
            incident.setResolvedAt(Instant.now());
        }
        return incidentRepository.save(incident);
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (!"TENANT_ADMIN".equals(actor.role()) && !"SECURITY_ADMIN".equals(actor.role())) {
            throw new ForbiddenException("Only a tenant admin can do this.");
        }
    }
}
