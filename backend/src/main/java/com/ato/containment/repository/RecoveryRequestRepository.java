package com.ato.containment.repository;

import com.ato.containment.model.RecoveryRequest;
import com.ato.containment.model.RecoveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecoveryRequestRepository extends JpaRepository<RecoveryRequest, String> {

    /** Used by the admin oversight view (GET /api/recovery/admin), tenant-scoped. */
    List<RecoveryRequest> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId);

    List<RecoveryRequest> findByUserIdAndStatus(String userId, RecoveryStatus status);

    Optional<RecoveryRequest> findByIdAndUserId(String id, String userId);
}
