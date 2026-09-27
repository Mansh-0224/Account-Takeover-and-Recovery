package com.ato.containment.repository;

import com.ato.containment.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    /** Used only at login, before any tenant is known yet. */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * The only way the rest of the app should ever fetch a single user by id.
     * Because the query itself filters by tenantId, a user id that belongs to
     * a different tenant simply is not found — the caller cannot tell the
     * difference between "wrong tenant" and "does not exist at all".
     */
    Optional<User> findByIdAndTenantId(String id, String tenantId);

    List<User> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
