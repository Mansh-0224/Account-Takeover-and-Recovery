/**
 * Runs on every dashboard-shell page. Calls the real backend to find out who
 * is logged in (GET /api/auth/me, which relies on the session cookie set at
 * login). If there is no valid session, this sends the browser back to the
 * login page — so the whole app is gated behind a real, server-verified login.
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
        window.location.href = "login.html";
        return;
    }

    const logoutBtn = document.getElementById("logout-btn");
    if (logoutBtn) {
        logoutBtn.addEventListener("click", async () => {
            try {
                await apiPost("/api/auth/logout");
            } catch (e) {
                // even if the request fails, still send the user back to the login page
            }
            window.location.href = "login.html";
        });
    }
});

function initialsOf(name) {
    return name.split(/[\s@.]+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join("");
}
