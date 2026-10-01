package com.ato.containment.security;

import com.ato.containment.exception.UnauthorizedException;
import com.ato.containment.model.Session;
import com.ato.containment.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the {@code Authorization: Bearer <token>} header, resolves it to a
 * real {@link Session} row via {@link SessionService}, and returns who that
 * session belongs to.
 *
 * This class is deliberately the ONLY way the rest of the backend learns
 * "which tenant is this request for?". A request body or query parameter is
 * never trusted for that — only a token the server itself handed out at
 * login, which the server can look up, expire, and revoke. That is what
 * makes tenant isolation trustworthy.
 */
@Component
public class CurrentSessionResolver {

    private final SessionService sessionService;

    public CurrentSessionResolver(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public AuthenticatedUser require(HttpServletRequest request) {
        String token = extractToken(request)
                .orElseThrow(() -> new UnauthorizedException("You must be logged in to do this."));

        Session session = sessionService.resolve(token)
                .orElseThrow(() -> new UnauthorizedException("Your session has expired or was revoked. Please log in again."));

        return new AuthenticatedUser(
                session.getUser().getId(),
                session.getTenantId(),
                session.getUser().getRole().getName(),
                session.getId());
    }

    /** Used by /api/auth/logout, which needs the raw token to find and revoke this exact session. */
    public Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
