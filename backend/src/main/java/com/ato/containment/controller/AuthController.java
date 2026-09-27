package com.ato.containment.controller;

import com.ato.containment.dto.LoginRequest;
import com.ato.containment.dto.UserDto;
import com.ato.containment.exception.UnauthorizedException;
import com.ato.containment.model.User;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSession;
import com.ato.containment.security.SessionKeys;
import com.ato.containment.service.AuthService;
import com.ato.containment.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login is the only place a tenant gets attached to a session. Everywhere
 * else in the app reads that tenant back out via CurrentSession — it is
 * never re-derived from anything the client sends after this point.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public UserDto login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        User user = authService.authenticate(request.email(), request.password())
                .orElseThrow(() -> new UnauthorizedException("Incorrect email or password."));

        // A fresh session per login. Its tenant/user/role values come only from
        // the database record we just verified — nothing from the request.
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        session.setAttribute(SessionKeys.TENANT_ID, user.getTenant().getId());
        session.setAttribute(SessionKeys.ROLE, user.getRole().getName());

        return UserDto.from(user);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/me")
    public UserDto me(HttpServletRequest request) {
        AuthenticatedUser current = CurrentSession.require(request);
        User user = userService.getForTenant(current.tenantId(), current.userId());
        return UserDto.from(user);
    }
}
