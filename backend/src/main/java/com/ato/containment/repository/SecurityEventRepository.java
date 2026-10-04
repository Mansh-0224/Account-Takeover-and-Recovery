package com.ato.containment.repository;

import com.ato.containment.model.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * {@link JpaSpecificationExecutor} gives us {@code findAll(Specification, Pageable)},
 * which SecurityEventService uses to build a flexible, tenant-scoped search
 * (event type / severity / user / date range, any combination) without a
 * combinatorial explosion of derived query methods.
 */
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, String>, JpaSpecificationExecutor<SecurityEvent> {
}
