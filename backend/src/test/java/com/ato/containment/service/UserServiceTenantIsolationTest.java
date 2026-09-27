package com.ato.containment.service;

import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.repository.RoleRepository;
import com.ato.containment.repository.TenantRepository;
import com.ato.containment.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Proves the core tenant-isolation rule at the service layer, which is where
 * it is actually enforced (UserController just forwards whatever this class
 * decides — see UserControllerTenantIsolationTest for that layer).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTenantIsolationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private RoleRepository roleRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, tenantRepository, roleRepository);
    }

    @Test
    void tenantA_readingItsOwnUser_isAllowed() {
        Tenant tenantA = new Tenant("Tenant A", "tenant-a");
        User user = new User();
        user.setTenant(tenantA);
        user.setEmail("person@tenant-a.example");

        when(userRepository.findByIdAndTenantId("user-1", tenantA.getId()))
                .thenReturn(Optional.of(user));

        User result = userService.getForTenant(tenantA.getId(), "user-1");

        assertThat(result).isSameAs(user);
    }

    @Test
    void tenantA_readingTenantBsUser_isDenied() {
        // Tenant A asks for a user id that actually belongs to Tenant B.
        // The repository call is scoped by tenantId, so it legitimately finds
        // nothing here -- exactly as if the id did not exist at all.
        when(userRepository.findByIdAndTenantId("user-in-tenant-b", "tenant-a-id"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getForTenant("tenant-a-id", "user-in-tenant-b"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
