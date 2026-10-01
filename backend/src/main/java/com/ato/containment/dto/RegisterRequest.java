package com.ato.containment.dto;

/**
 * What a client sends to sign up. This creates a brand-new tenant (the
 * caller's company) with the caller as its first TENANT_ADMIN — see
 * AuthController#register.
 */
public record RegisterRequest(String companyName, String email, String password, String fullName) {
}
