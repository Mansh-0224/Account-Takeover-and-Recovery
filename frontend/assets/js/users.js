/**
 * Renders the users table from the real backend (GET /api/users). The
 * backend only ever returns users belonging to the logged-in session's own
 * tenant — that's tenant isolation, enforced server-side, not something this
 * script has to worry about.
 *
 * Risk level/score aren't modeled by the backend yet (that's a later phase),
 * so those columns just show "—" for now instead of made-up numbers.
 */
document.addEventListener("DOMContentLoaded", async () => {
    const tbody = document.getElementById("users-table-body");

    try {
        const users = await apiGet("/api/users");

        if (users.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="cell-muted" style="text-align:center;padding:1.5rem;">No users in this tenant yet.</td></tr>';
            return;
        }

        tbody.innerHTML = users.map(rowFor).join("");
    } catch (error) {
        tbody.innerHTML = '<tr><td colspan="8" class="cell-muted" style="text-align:center;padding:1.5rem;color:var(--high);">Couldn\u2019t load users from the backend.</td></tr>';
    }
});

function rowFor(user) {
    const initials = (user.fullName || user.email).split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join("");
    const statusClass = "badge-" + user.status.toLowerCase();
    return `<tr>
      <td>
        <div style="display:flex;align-items:center;gap:.6rem;">
          <div class="avatar" style="width:30px;height:30px;">${escapeHtml(initials)}</div>
          <div>
            <div class="cell-primary">${escapeHtml(user.fullName || "(no name)")}</div>
            <div class="cell-muted" style="font-size:.76rem;">${escapeHtml(user.email)}</div>
          </div>
        </div>
      </td>
      <td class="cell-muted">${escapeHtml(user.tenantName)}</td>
      <td class="cell-muted">${escapeHtml(roleLabel(user.role))}</td>
      <td><span class="badge ${statusClass}">${escapeHtml(user.status.replace(/_/g, " "))}</span></td>
      <td class="cell-muted">&mdash;</td>
      <td class="cell-muted">&mdash;</td>
      <td class="cell-muted">&mdash;</td>
      <td><a href="user-detail.html?id=${encodeURIComponent(user.id)}" class="link-sm">View details &rarr;</a></td>
    </tr>`;
}

function roleLabel(role) {
    return role.replace(/_/g, " ").replace(/\w\S*/g, w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase());
}

function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
