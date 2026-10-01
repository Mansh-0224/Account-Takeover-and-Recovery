/**
 * Runs on every dashboard-shell page. Calls the real backend to find out who
 * is logged in (GET /api/auth/me, sent with the stored bearer token — see
 * api.js). If the token is missing, expired, or was revoked, this clears it
 * and sends the browser back to the login page — so the whole app is gated
 * behind a real, server-verified session.
 */
document.addEventListener("DOMContentLoaded", async () => {
    try {
        const user = await apiGet("/api/auth/me");
        const nameEl = document.getElementById("user-name");
        const avatarEl = document.getElementById("user-avatar");
        const tenantEl = document.getElementById("tenant-tag");
        if (nameEl) nameEl.textContent = user.fullName || user.email;
        if (avatarEl) avatarEl.textContent = initialsOf(user.fullName || user.email);
        if (tenantEl) tenantEl.textContent = user.tenantName;
    } catch (error) {
        // Important: clear the token before leaving, otherwise login.html's
        // "already signed in?" check would see this same stale token and
        // bounce straight back here, causing a redirect loop.
        clearSessionToken();
        window.location.href = "login.html";
        return;
    }

    const logoutBtn = document.getElementById("logout-btn");
    if (logoutBtn) {
        logoutBtn.addEventListener("click", async () => {
            try {
                await apiPost("/api/auth/logout"); // revokes just this one session, server-side
            } catch (e) {
                // even if the request fails, still log out locally
            }
            clearSessionToken();
            window.location.href = "login.html";
        });
    }
});

function initialsOf(name) {
    return name.split(/[\s@.]+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join("");
}
