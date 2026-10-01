package com.ato.containment.controller;

import com.ato.containment.exception.ResourceNotFoundException;
import com.ato.containment.exception.UnauthorizedException;
import com.ato.containment.model.Role;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import com.ato.containment.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves tenant isolation at the HTTP layer:
 *   - Tenant A -> Tenant A's own resource = ALLOW (200)
 *   - Tenant A -> Tenant B's resource     = DENY  (404)
 *
 * and, separately, that a tenantId a client tries to supply is never used —
 * only whatever CurrentSessionResolver resolves from the real session reaches
 * the service layer. CurrentSessionResolver itself is mocked here so this
 * test can focus purely on the controller's behavior; the resolver's own
 * token-lookup logic is exercised for real in SessionServiceTest.
 */
@WebMvcTest(UserController.class)
class UserControllerTenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CurrentSessionResolver currentSessionResolver;

    private AuthenticatedUser adminOf(String tenantId) {
        return new AuthenticatedUser("admin-1", tenantId, "TENANT_ADMIN", "session-1");
    }

    private User userIn(Tenant tenant) {
        User user = new User();
        user.setTenant(tenant);
        user.setEmail("person@example.com");
        user.setFullName("Sample Person");
        user.setRole(new Role("USER", "Normal user"));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void tenantA_readingItsOwnUser_isAllowed() throws Exception {
        Tenant tenantA = new Tenant("Tenant A", "tenant-a");
        given(currentSessionResolver.require(any(HttpServletRequest.class))).willReturn(adminOf(tenantA.getId()));
        given(userService.getForTenant(tenantA.getId(), "user-1")).willReturn(userIn(tenantA));

        mockMvc.perform(get("/api/users/user-1").header("Authorization", "Bearer fake-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantA.getId()));
    }

    @Test
    void tenantA_readingTenantBsUser_isDenied() throws Exception {
        Tenant tenantA = new Tenant("Tenant A", "tenant-a");
        given(currentSessionResolver.require(any(HttpServletRequest.class))).willReturn(adminOf(tenantA.getId()));
        given(userService.getForTenant(tenantA.getId(), "user-in-tenant-b"))
                .willThrow(new ResourceNotFoundException("No user found with this id."));

        mockMvc.perform(get("/api/users/user-in-tenant-b").header("Authorization", "Bearer fake-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantId_isAlwaysTakenFromTheResolvedSession_neverFromTheClient() throws Exception {
        Tenant tenantA = new Tenant("Tenant A", "tenant-a");
        given(currentSessionResolver.require(any(HttpServletRequest.class))).willReturn(adminOf(tenantA.getId()));
        given(userService.getForTenant(eq(tenantA.getId()), eq("user-1"))).willReturn(userIn(tenantA));

        // A request tries to sneak in a different tenant as a query parameter.
        // The controller never reads it -- only CurrentSessionResolver counts,
        // and that's mocked above to always resolve to Tenant A regardless.
        mockMvc.perform(get("/api/users/user-1?tenantId=someone-elses-tenant").header("Authorization", "Bearer fake-token"))
                .andExpect(status().isOk());

        verify(userService).getForTenant(eq(tenantA.getId()), eq("user-1"));
    }

    @Test
    void noValidSession_isRejectedWithUnauthorized() throws Exception {
        given(currentSessionResolver.require(any(HttpServletRequest.class)))
                .willThrow(new UnauthorizedException("You must be logged in to do this."));

        mockMvc.perform(get("/api/users/user-1"))
                .andExpect(status().isUnauthorized());
    }
}
