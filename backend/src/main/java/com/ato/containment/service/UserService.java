package com.ato.containment.service;

import com.ato.containment.dto.CreateUserRequest;
import com.ato.containment.dto.UpdateUserRequest;
import com.ato.containment.exception.ForbiddenException;
import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.model.Role;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RoleRepository;
import com.ato.containment.repository.TenantRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.security.AuthenticatedUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * All reads and writes to users go through this class, and every one of them
 * is scoped to a tenant. This is where tenant isolation is actually enforced:
 *
 *   - Reads use UserRepository#findByIdAndTenantId, so a user id belonging to
 *     another tenant is treated exactly like an id that does not exist.
 *   - Writes always attach the ACTOR's own tenant (from the session), never
 *     a tenant taken from the request.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, TenantRepository tenantRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
    }

    /** Every user belonging to this tenant. Never any other tenant's users. */
    public List<User> listForTenant(String tenantId) {
        return userRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * Fetches one user, scoped to the given tenant. If {@code userId} exists
     * but belongs to a different tenant, this throws exactly the same
     * exception as if it did not exist at all (404, not 403) — see
     * docs/06-threat-scenarios.md, TS-6.
     */
    public User getForTenant(String tenantId, String userId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No user found with this id."));
    }

    public User create(AuthenticatedUser actor, CreateUserRequest request) {
        requireAdmin(actor);

        Tenant tenant = tenantRepository.findById(actor.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found."));
        Role role = roleRepository.findById(request.role())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown role: " + request.role()));

        User user = new User();
        user.setTenant(tenant); // always the actor's own tenant — request has no tenantId field to begin with
        user.setEmail(request.email().toLowerCase());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    public User update(AuthenticatedUser actor, String userId, UpdateUserRequest request) {
        requireAdmin(actor);
        User user = getForTenant(actor.tenantId(), userId); // also blocks editing another tenant's user

        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.role() != null) {
            Role role = roleRepository.findById(request.role())
                    .orElseThrow(() -> new ResourceNotFoundException("Unknown role: " + request.role()));
            user.setRole(role);
        }
        return userRepository.save(user);
    }

    public void delete(AuthenticatedUser actor, String userId) {
        requireAdmin(actor);
        User user = getForTenant(actor.tenantId(), userId); // also blocks deleting another tenant's user
        userRepository.delete(user);
    }

    private void requireAdmin(AuthenticatedUser actor) {
        if (!"TENANT_ADMIN".equals(actor.role()) && !"SECURITY_ADMIN".equals(actor.role())) {
            throw new ForbiddenException("Only a tenant admin can do this.");
        }
    }
}
