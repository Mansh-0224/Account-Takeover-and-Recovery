/**
 * Reads ?id= from the URL and fetches that one user from the real backend.
 * If the id belongs to a different tenant than the logged-in session, the
 * backend returns 404 — this page just shows that as "account not found",
 * which is exactly the point: it should look identical to an id that never
 * existed at all (see docs/06-threat-scenarios.md, TS-6).
 *
 * The header, "Account details" card, and risk score/signals cards are real.
 * Login history and sessions further down are still sample placeholders —
 * those views aren't implemented yet.
 */
document.addEventListener("DOMContentLoaded", async () => {
    const params = new URLSearchParams(window.location.search);
    const id = params.get("id");
    const errorBox = document.getElementById("detail-load-error");

    if (!id) {
        showError("No account id was given in the link.");
        return;
    }

    try {
        const user = await apiGet("/api/users/" + encodeURIComponent(id));
        render(user);
        renderRisk(id);
    } catch (error) {
        showError(error.status === 404
            ? "No account found for this id. It may belong to a different tenant, or the link is out of date."
            : "Couldn\u2019t load this account from the backend.");
    }

    async function renderRisk(userId) {
        const scoreBody = document.getElementById("risk-score-body");
        const scoreHint = document.getElementById("risk-score-hint");
        const signalsBody = document.getElementById("risk-signals-body");
        const signalsHint = document.getElementById("risk-signals-hint");

        let assessments = [];
        try {
            assessments = await apiGet("/api/risk-assessments?userId=" + encodeURIComponent(userId));
        } catch (error) {
            scoreBody.innerHTML = '<p class="hint" style="color:var(--high);">Couldn\u2019t load risk data.</p>';
            signalsBody.innerHTML = "";
            return;
        }

        if (assessments.length === 0) {
            scoreHint.textContent = "no assessments yet";
            scoreBody.innerHTML = '<p class="hint">No risk assessments recorded yet. Log in as this user to generate one.</p>';
            signalsHint.textContent = "";
            signalsBody.innerHTML = "";
            return;
        }

        const latest = assessments[0];
        const pct = Math.max(0, Math.min(100, latest.score));
        const radius = 34, circumference = 2 * Math.PI * radius;
        const offset = circumference * (1 - pct / 100);
        const color = latest.riskLevel === "HIGH" ? "var(--high)" : latest.riskLevel === "MEDIUM" ? "var(--medium)" : "var(--low)";

        scoreHint.textContent = new Date(latest.createdAt).toLocaleString();
        scoreBody.innerHTML = `
          <div class="risk-ring-wrap">
            <svg width="88" height="88" viewBox="0 0 80 80">
              <circle cx="40" cy="40" r="${radius}" fill="none" stroke="var(--surface-raised)" stroke-width="8"/>
              <circle cx="40" cy="40" r="${radius}" fill="none" stroke="${color}" stroke-width="8" stroke-linecap="round"
                stroke-dasharray="${circumference}" stroke-dashoffset="${offset}" transform="rotate(-90 40 40)"/>
              <text x="40" y="45" text-anchor="middle" font-size="20" font-weight="700" fill="var(--ink)">${pct}</text>
            </svg>
            <div>
              <div class="risk-ring-value" style="color:${color}">${latest.riskLevel}</div>
              <div class="risk-ring-label">Decision: ${latest.decision} · triggered by ${latest.triggerType}</div>
            </div>
          </div>`;

        signalsHint.textContent = latest.signals.length + " triggered";
        signalsBody.innerHTML = latest.signals.length
            ? latest.signals.map(s => `<div class="signal-chip"><div><div class="signal-title">${signalLabelDetail(s)}</div></div><div class="signal-points">+${signalPointsDetail(s)}</div></div>`).join("")
            : '<p class="hint">No signals triggered for the most recent assessment.</p>';
    }

    function showError(message) {
        document.getElementById("detail-name").textContent = "Account not found";
        errorBox.textContent = message;
        errorBox.style.display = "block";
    }

    function render(user) {
        const initials = (user.fullName || user.email).split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join("");
        document.getElementById("detail-avatar").textContent = initials;
        document.getElementById("detail-name").textContent = user.fullName || "(no name)";
        document.getElementById("detail-email").textContent = user.email;

        const statusBadge = document.getElementById("detail-status-badge");
        statusBadge.textContent = user.status.replace(/_/g, " ");
        statusBadge.className = "badge badge-" + user.status.toLowerCase();

        document.getElementById("detail-tenant").textContent = user.tenantName;
        document.getElementById("detail-role").textContent = roleLabel(user.role);
        document.getElementById("detail-created").textContent = new Date(user.createdAt).toLocaleString();
        document.getElementById("detail-id").textContent = user.id;

        const actionSlot = document.getElementById("detail-action-btn");
        const note = document.getElementById("detail-containment-note");
        const actions = document.getElementById("detail-containment-actions");

        if (user.status === "ACTIVE") {
            actionSlot.innerHTML = `<a href="containment.html" class="btn btn-sm btn-danger">Contain account</a>`;
            note.textContent = "This account is active. Containment and recovery aren't wired to real accounts yet — the linked pages use sample data.";
            actions.innerHTML = `<a href="containment.html" class="btn btn-sm btn-danger">Go to containment</a>`;
        } else {
            actionSlot.innerHTML = `<a href="recovery.html" class="btn btn-sm btn-primary">Start recovery</a>`;
            note.textContent = "This account is " + user.status.replace(/_/g, " ").toLowerCase() + ". Containment and recovery aren't wired to real accounts yet — the linked pages use sample data.";
            actions.innerHTML = `<a href="recovery.html" class="btn btn-sm">Go to recovery</a>`;
        }
    }

    function roleLabel(role) {
        return role.replace(/_/g, " ").replace(/\w\S*/g, w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase());
    }
});

function signalLabelDetail(name) {
    return name.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}
function signalPointsDetail(name) {
    return { NEW_DEVICE: 20, UNUSUAL_LOCATION: 20, MULTIPLE_FAILED_LOGINS: 20, PASSWORD_CHANGED: 30, SUSPICIOUS_SESSION: 25, TOKEN_REPLAY: 50 }[name] || "?";
}
