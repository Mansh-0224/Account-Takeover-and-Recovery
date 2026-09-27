package com.ato.containment.security;

/**
 * Names of the HttpSession attributes set at login (see AuthController) and
 * read everywhere else (see CurrentSession). Keeping the names in one place
 * avoids typos causing a silent mismatch between writer and reader.
 */
public final class SessionKeys {

    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String ROLE = "role";

    private SessionKeys() {
    }
}
