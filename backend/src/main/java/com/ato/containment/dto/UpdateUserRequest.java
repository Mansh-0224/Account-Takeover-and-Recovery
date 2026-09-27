package com.ato.containment.dto;

import com.ato.containment.model.UserStatus;

/**
 * Fields a client may update on an existing user. Any field left null is left
 * unchanged. There is no tenantId here either, for the same reason as
 * CreateUserRequest — you cannot move a user to a different tenant by editing it.
 */
public record UpdateUserRequest(String fullName, String role, UserStatus status) {
}
