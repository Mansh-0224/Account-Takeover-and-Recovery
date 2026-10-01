package com.ato.containment.controller;

import com.ato.containment.dto.ChangePasswordRequest;
import com.ato.containment.dto.LoginRequest;
import com.ato.containment.dto.LoginResponse;
import com.ato.containment.dto.RegisterRequest;
import com.ato.containment.dto.UserDto;
import com.ato.containment.exception.UnauthorizedException;
import com.ato.containment.model.Role;
import com.ato.containment.model.Tenant;
import com.ato.containment.model.User;
import com.ato.containment.model.UserStatus;
import com.ato.containment.repository.RoleRepository;
import com.ato.containment.repository.TenantRepository;
import com.ato.containment.repository.UserRepository;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import com.ato.containment.service.AuthService;
import com.ato.containment.service.SessionService;
import com.ato.containment.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and registration are the only places a tenant gets attached to a
 * session. Everywhere else in the app reads that tenant back out via
 * {@link CurrentSessionResolver} — it is never re-derived from anything the
 * client sends after this point.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final SessionService sessionService;
    private final CurrentSessionResolver currentSessionResolver;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserService userService, SessionService sessionService,
                          CurrentSessionResolver currentSessionResolver, TenantRepository tenantRepository,
                          RoleRepository roleRepository, UserRepository userRepository) {
        this.authService = authService;
        this.userService = userService;
        this.sessionService = sessionService;
        this.currentSessionResolver = currentSessionResolver;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a brand-new tenant (the caller's company) with the caller as
     * its first TENANT_ADMIN, then logs them straight in.
     */
    @PostMapping("/register")
    public LoginResponse register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        if (body.email() == null || body.password() == null || body.companyName() == null
                || body.email().isBlank() || body.password().isBlank() || body.companyName().isBlank()) {
            throw new IllegalArgumentException("Company name, email, and password are all required.");
        }
        if (body.password().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        if (userRepository.findByEmailIgnoreCase(body.email()).isPresent()) {
            throw new IllegalArgumentException("That email is already in use.");
        }

        Tenant tenant = tenantRepository.save(new Tenant(body.companyName(), uniqueSlug(body.companyName())));
        Role tenantAdmin = roleRepository.findById("TENANT_ADMIN")
                .orElseThrow(() -> new IllegalStateException("TENANT_ADMIN role is missing — check DataSeeder."));

        User user = new User();
        user.setTenant(tenant);
        user.setEmail(body.email().toLowerCase());
        user.setFullName(body.fullName());
        user.setPasswordHash(authService.passwordEncoder().encode(body.password()));
        user.setRole(tenantAdmin);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        return startSession(user, request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        User user = authService.authenticate(request.email(), request.password())
                .orElseThrow(() -> new UnauthorizedException("Incorrect email or password."));
        return startSession(user, httpRequest);
    }

    /** Logs out only the session making this request — "log out this device". */
    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        currentSessionResolver.extractToken(request).ifPresent(token ->
                sessionService.resolve(token).ifPresent(session ->
                        sessionService.revoke(session.getUser().getId(), session.getId())));
    }

    /** Logs out every session for the current user — "log out everywhere". */
    @PostMapping("/logout-all")
    public void logoutAll(HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        sessionService.revokeAllForUser(current.userId(), null);
    }

    @GetMapping("/me")
    public UserDto me(HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        User user = userService.getForTenant(current.tenantId(), current.userId());
        return UserDto.from(user);
    }

    /**
     * Changes the current user's password after checking the current one.
     * All OTHER sessions are revoked afterwards (a sensible default: if the
     * password just changed, anything logged in under the old one should
     * need to log in again) — the session making this request is left alone.
     */
    @PutMapping("/password")
    public void changePassword(@RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        User user = userService.getForTenant(current.tenantId(), current.userId());
        authService.changePassword(user, body.currentPassword(), body.newPassword());
        sessionService.revokeAllForUser(current.userId(), current.sessionId());
    }

    private LoginResponse startSession(User user, HttpServletRequest request) {
        String deviceId = request.getHeader("X-Device-Id");
        String ipAddress = request.getRemoteAddr();
        String location = request.getHeader("X-Simulated-Location"); // dev-only simulated geo, see docs decision D5
        String userAgent = request.getHeader("User-Agent");

        SessionService.NewSession newSession = sessionService.create(user, deviceId, ipAddress, location, userAgent);
        return new LoginResponse(newSession.rawToken(), UserDto.from(user));
    }

    private String uniqueSlug(String companyName) {
        String base = companyName.toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "tenant";
        }
        String slug = base;
        int suffix = 2;
        while (tenantRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + suffix;
            suffix++;
        }
        return slug;
    }
}
