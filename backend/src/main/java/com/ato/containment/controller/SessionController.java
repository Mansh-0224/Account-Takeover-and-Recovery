package com.ato.containment.controller;

import com.ato.containment.dto.SessionDto;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import com.ato.containment.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A user's own sessions only — there is no endpoint here for viewing or
 * revoking someone else's session, in the same tenant or otherwise.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final CurrentSessionResolver currentSessionResolver;

    public SessionController(SessionService sessionService, CurrentSessionResolver currentSessionResolver) {
        this.sessionService = sessionService;
        this.currentSessionResolver = currentSessionResolver;
    }

    @GetMapping
    public List<SessionDto> list(HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return sessionService.listForUser(current.userId()).stream()
                .map(session -> SessionDto.from(session, session.getId().equals(current.sessionId())))
                .toList();
    }

    /** Revoke one specific session (must belong to the caller). */
    @DeleteMapping("/{id}")
    public void revokeOne(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        sessionService.revoke(current.userId(), id);
    }

    /** Revoke every one of the caller's sessions, including the current one — "log out everywhere". */
    @DeleteMapping
    public void revokeAll(HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        sessionService.revokeAllForUser(current.userId(), null);
    }
}
