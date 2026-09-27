package com.ato.containment.security;

import com.ato.containment.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Reads the logged-in user's identity from the HttpSession that was created
 * at login (see AuthController#login).
 *
 * This class is deliberately the ONLY way the rest of the backend learns
 * "which tenant is this request for?". A request body or query parameter is
 * never trusted for that — a client could put any value there. The session,
 * on the other hand, was set by the server itself at login and cannot be
 * edited by the browser, which is what makes tenant isolation trustworthy.
 */
public final class CurrentSession {

    private CurrentSession() {
    }

    public static AuthenticatedUser require(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // false: do not create one if missing
        if (session == null) {
            throw new UnauthorizedException("You must be logged in to do this.");
        }

        String userId = (String) session.getAttribute(SessionKeys.USER_ID);
        String tenantId = (String) session.getAttribute(SessionKeys.TENANT_ID);
        String role = (String) session.getAttribute(SessionKeys.ROLE);

        if (userId == null || tenantId == null) {
            throw new UnauthorizedException("Your session has expired. Please log in again.");
        }
        return new AuthenticatedUser(userId, tenantId, role);
    }
}
