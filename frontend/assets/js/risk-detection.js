/**
 * Loads real users (GET /api/users) into the selector, then real risk
 * assessment history (GET /api/risk-assessments?userId=) for whichever one
 * is picked. Both endpoints are tenant-scoped server-side.
 */
document.addEventListener("DOMContentLoaded", async () => {
    const select = document.getElementById("account-select");
    const root = document.getElementById("risk-detection-root");

    let users = [];
    try {
        users = await apiGet("/api/users");
    } catch (error) {
        select.innerHTML = '<option>Couldn\u2019t load users</option>';
        root.innerHTML = '<p class="hint" style="color:var(--high);">Couldn\u2019t reach the backend.</p>';
        return;
    }

    if (users.length === 0) {
        select.innerHTML = '<option>No users in this tenant</option>';
        return;
    }

    select.innerHTML = users.map(u => `<option value="${u.id}">${escapeHtmlRisk(u.fullName || u.email)}</option>`).join("");

    async function renderFor(userId) {
        root.innerHTML = '<p class="hint">Loading risk history…</p>';
        let assessments = [];
        try {
            assessments = await apiGet("/api/risk-assessments?userId=" + encodeURIComponent(userId));
        } catch (error) {
            root.innerHTML = '<p class="hint" style="color:var(--high);">Couldn\u2019t load risk history.</p>';
            return;
        }

        if (assessments.length === 0) {
            root.innerHTML = `<div class="card"><p class="hint">No risk assessments recorded yet for this account. Log in as this user (any seeded account uses password <code>demo1234</code>) to generate one.</p></div>`;
            return;
        }

        const latest = assessments[0];
        const pct = Math.max(0, Math.min(100, latest.score));
        const radius = 46, circumference = 2 * Math.PI * radius;
        const offset = circumference * (1 - pct / 100);
        const color = latest.riskLevel === "HIGH" ? "var(--high)" : latest.riskLevel === "MEDIUM" ? "var(--medium)" : "var(--low)";
        const decisionBadge = { ALLOW: "badge-low", CHALLENGE: "badge-medium", BLOCK: "badge-high" }[latest.decision] || "badge-neutral";

        const signalRows = latest.signals.length
            ? latest.signals.map(s => `<div class="signal-chip"><div><div class="signal-title">${signalLabel(s)}</div></div><div class="signal-points">+${signalPoints(s)}</div></div>`).join("")
            : '<p class="hint">No signals triggered for this assessment.</p>';

        const historyRows = assessments.slice(0, 10).map(a => `
            <tr>
              <td class="cell-muted">${new Date(a.createdAt).toLocaleString()}</td>
              <td class="cell-muted">${a.triggerType}</td>
              <td class="mono">${a.score}</td>
              <td><span class="badge badge-${a.riskLevel.toLowerCase()}">${a.riskLevel}</span></td>
              <td><span class="badge ${({ALLOW:"badge-low",CHALLENGE:"badge-medium",BLOCK:"badge-high"})[a.decision]}">${a.decision}</span></td>
            </tr>`).join("");

        root.innerHTML = `
          <div class="grid grid-2">
            <div class="card">
              <div class="card-header"><h2>Latest detected signals</h2><span class="hint">${latest.signals.length} triggered</span></div>
              <div style="display:flex;flex-direction:column;gap:.55rem;">${signalRows}</div>
            </div>
            <div class="card">
              <div class="card-header"><h2>Risk assessment</h2></div>
              <div class="risk-ring-wrap" style="margin-bottom:1rem;">
                <svg width="110" height="110" viewBox="0 0 100 100">
                  <circle cx="50" cy="50" r="${radius}" fill="none" stroke="var(--surface-raised)" stroke-width="9"/>
                  <circle cx="50" cy="50" r="${radius}" fill="none" stroke="${color}" stroke-width="9" stroke-linecap="round"
                    stroke-dasharray="${circumference}" stroke-dashoffset="${offset}" transform="rotate(-90 50 50)"/>
                  <text x="50" y="57" text-anchor="middle" font-size="24" font-weight="700" fill="var(--ink)">${pct}</text>
                </svg>
                <div>
                  <div class="risk-ring-value" style="color:${color}">${latest.riskLevel} RISK</div>
                  <div class="risk-ring-label">${new Date(latest.createdAt).toLocaleString()}</div>
                </div>
              </div>
              <div class="kv-list">
                <div class="kv-row"><span class="k">Decision</span><span><span class="badge ${decisionBadge}">${latest.decision}</span></span></div>
                <div class="kv-row"><span class="k">Triggered by</span><span>${latest.triggerType}</span></div>
              </div>
              <a href="user-detail.html?id=${userId}" class="btn btn-sm mt-lg" style="margin-top:1.1rem;">View full account &rarr;</a>
            </div>
          </div>
          <div class="card mt-lg">
            <div class="card-header"><h2>Assessment history</h2><span class="hint">${assessments.length} total</span></div>
            <div class="table-wrap">
              <table><thead><tr><th>When</th><th>Trigger</th><th>Score</th><th>Level</th><th>Decision</th></tr></thead>
              <tbody>${historyRows}</tbody></table>
            </div>
          </div>`;
    }

    select.addEventListener("change", () => renderFor(select.value));
    renderFor(select.value);
});

function signalLabel(name) {
    return name.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}
function signalPoints(name) {
    return { NEW_DEVICE: 20, UNUSUAL_LOCATION: 20, MULTIPLE_FAILED_LOGINS: 20, PASSWORD_CHANGED: 30, SUSPICIOUS_SESSION: 25, TOKEN_REPLAY: 50 }[name] || "?";
}
function escapeHtmlRisk(str) {
    return String(str).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
