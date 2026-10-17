package com.ato.containment.repository;

import com.ato.containment.model.Incident;
import com.ato.containment.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, String> {

    List<Incident> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * The only way an incident should ever be fetched for viewing/updating:
     * scoped to the tenant. An incident id belonging to another tenant is
     * simply not found, same as a nonexistent id.
     */
    Optional<Incident> findByIdAndTenantId(String id, String tenantId);

    /**
     * Used to avoid opening a duplicate ACCOUNT_TAKEOVER incident for a user
     * who already has one unresolved (see incident/IncidentService).
     */
    List<Incident> findByTenantIdAndUserIdAndStatusIn(String tenantId, String userId, List<IncidentStatus> statuses);
}
