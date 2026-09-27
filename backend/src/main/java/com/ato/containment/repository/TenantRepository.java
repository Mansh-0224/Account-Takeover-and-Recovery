package com.ato.containment.repository;

import com.ato.containment.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlug(String slug);
}
