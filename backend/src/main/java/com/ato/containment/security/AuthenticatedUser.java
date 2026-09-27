package com.ato.containment.security;

/**
 * Who is making the current request, as resolved from the server-side session.
 * Every controller and service that needs to know "which tenant is this?"
 * should get it from here, never from a request parameter or body field.
 */
public record AuthenticatedUser(String userId, String tenantId, String role) {
}
