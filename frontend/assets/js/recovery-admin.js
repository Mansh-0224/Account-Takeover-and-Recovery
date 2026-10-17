/**
 * Read-only admin view of a user's recovery history. Deliberately has no
 * "advance" button -- an admin should never be able to push someone else's
 * recovery past the owner's own code verification; that would defeat the
 * point of self-service verification. The actual recovery steps only run
 * through forgot-password.html, by the account owner themselves.
 */
document.addEventListener("DOMContentLoaded", async () => {
    const select = document.getElementById("account-select");
    const root = document.getElementById("recovery-root");

    let users = [];
    try {
        users = await apiGet("/api/users");
    } catch (error) {
        select.innerHTML = "<option>Couldn't load users</option>";
        return;
    }
    if (users.length === 0) {
        select.innerHTML = "<option>No users in this tenant</option>";
        return;
    }
    select.innerHTML = users.map(u => `<option value="${u.id}">${escapeHtmlRec(u.fullName || u.email)} — ${u.status.replace(/_/g, " ")}</option>`).join("");

    async function renderFor(userId) {
        root.innerHTML = '<p class="hint">Loading recovery history…</p>';
        let history = [];
        try {
            history = await apiGet("/api/recovery/admin?userId=" + encodeURIComponent(userId));
        } catch (error) {
            root.innerHTML = '<p class="hint" style="color:var(--high);">Couldn\u2019t load recovery history.</p>';
            return;
        }

        if (history.length === 0) {
            root.innerHTML = '<div class="card"><p class="hint">No recovery requests for this account yet.</p></div>';
            return;
        }

        const rows = history.map(r => `
          <tr>
            <td class="mono cell-muted">${r.requestId.slice(0, 8)}…</td>
            <td><span class="badge ${r.status === 'COMPLETED' ? 'badge-low' : r.status === 'FAILED' || r.status === 'EXPIRED' ? 'badge-neutral' : r.status === 'VERIFIED' ? 'badge-info' : 'badge-medium'}">${r.status}</span></td>
            <td class="cell-muted">${new Date(r.expiresAt).toLocaleString()}</td>
            <td class="mono">${r.attemptCounter}</td>
            <td class="cell-muted">${new Date(r.createdAt).toLocaleString()}</td>
            <td class="cell-muted">${r.completedAt ? new Date(r.completedAt).toLocaleString() : "\u2014"}</td>
          </tr>`).join("");

        root.innerHTML = `
          <div class="card">
            <div class="card-header"><h2>Recovery history</h2><span class="hint">${history.length} request(s), newest first</span></div>
            <div class="table-wrap">
              <table><thead><tr><th>Request</th><th>Status</th><th>Expires</th><th>Attempts</th><th>Created</th><th>Completed</th></tr></thead>
              <tbody>${rows}</tbody></table>
            </div>
          </div>`;
    }

    select.addEventListener("change", () => renderFor(select.value));
    renderFor(select.value);
});

function escapeHtmlRec(str) {
    return String(str).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
