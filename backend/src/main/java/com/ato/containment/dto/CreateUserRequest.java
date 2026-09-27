package com.ato.containment.dto;

/**
 * What a client sends to create a user.
 *
 * NOTE: there is deliberately no {@code tenantId} field here. A new user
 * always belongs to the tenant of the admin creating it — see
 * UserService#create — so there is nothing for a client to override even if
 * it tried to send one.
 */
public record CreateUserRequest(String email, String password, String fullName, String role) {
}
