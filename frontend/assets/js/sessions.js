/**
 * Renders the current user's own sessions from the real backend
 * (GET /api/sessions). There is no view of other users' sessions here, by
 * design — see SessionController.
 */
document.addEventListener("DOMContentLoaded", () => {
    const tbody = document.getElementById("sessions-table-body");
    const logoutAllBtn = document.getElementById("logout-all-btn");

    async function loadSessions() {
        try {
            const sessions = await apiGet("/api/sessions");
            if (sessions.length === 0) {
                tbody.innerHTML = '<tr><td colspan="8" class="cell-muted" style="text-align:center;padding:1.5rem;">No sessions found.</td></tr>';
                return;
            }
            tbody.innerHTML = sessions.map(rowFor).join("");
            tbody.querySelectorAll("[data-revoke]").forEach(btn => {
                btn.addEventListener("click", () => revokeOne(btn.getAttribute("data-revoke")));
            });
        } catch (error) {
            tbody.innerHTML = '<tr><td colspan="8" class="cell-muted" style="text-align:center;padding:1.5rem;color:var(--high);">Couldn\u2019t load sessions from the backend.</td></tr>';
        }
    }

    async function revokeOne(sessionId) {
        try {
            await apiDelete("/api/sessions/" + encodeURIComponent(sessionId));
            await loadSessions();
        } catch (error) {
            alert("Couldn't revoke that session: " + error.message);
        }
    }

    logoutAllBtn.addEventListener("click", async () => {
        if (!confirm("Log out of every session, including this one? You will need to sign in again.")) return;
        try {
            await apiDelete("/api/sessions");
        } catch (error) {
            // even if the request fails partway, fall through and clear locally
        }
        clearSessionToken();
        window.location.href = "login.html";
    });

    loadSessions();
});

function rowFor(session) {
    const statusClass = "badge-" + session.status.toLowerCase();
    const canRevoke = session.status === "ACTIVE";
    return `<tr>
      <td class="mono cell-primary">${escapeHtmlSession(session.sessionId)}</td>
      <td class="cell-muted">you</td>
      <td class="cell-muted">${escapeHtmlSession(session.userAgent || "Unknown device")}</td>
      <td class="mono cell-muted">${escapeHtmlSession(session.ip || "\u2014")}</td>
      <td class="cell-muted">${escapeHtmlSession(session.location || "\u2014")}</td>
      <td class="cell-muted">${session.lastSeen ? new Date(session.lastSeen).toLocaleString() : "\u2014"}</td>
      <td><span class="badge ${statusClass}">${session.status}${session.current ? " · this device" : ""}</span></td>
      <td>${canRevoke ? `<button class="btn btn-sm btn-danger" data-revoke="${session.sessionId}">Revoke</button>` : "&mdash;"}</td>
    </tr>`;
}

function escapeHtmlSession(str) {
    return String(str).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
