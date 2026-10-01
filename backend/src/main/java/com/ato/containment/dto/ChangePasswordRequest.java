package com.ato.containment.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
