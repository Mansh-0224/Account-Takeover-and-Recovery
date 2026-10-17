document.addEventListener("DOMContentLoaded", () => {
    const tbody = document.getElementById("incidents-table-body");
    const searchInput = document.getElementById("search-input");
    const statusFilter = document.getElementById("status-filter");
    const severityFilter = document.getElementById("severity-filter");
    const detailPanel = document.getElementById("incident-detail-panel");

    const NEXT_ACTION = {
        OPEN: { label: "Start investigating", action: "investigate" },
        INVESTIGATING: { label: "Mark contained", action: "contain" },
        CONTAINED: { label: "Move to recovery", action: "recover" },
        RECOVERED: { label: "Close incident", action: "close" },
    };

    let allIncidents = [];

    async function loadIncidents() {
        try {
            allIncidents = await apiGet("/api/incidents");
            render();
        } catch (error) {
            tbody.innerHTML = '<tr><td colspan="7" class="cell-muted" style="text-align:center;padding:1.5rem;color:var(--high);">Couldn\u2019t load incidents.</td></tr>';
        }
    }

    function render() {
        const q = searchInput.value.trim().toLowerCase();
        const status = statusFilter.value;
        const severity = severityFilter.value;

        const filtered = allIncidents.filter(i => {
            if (q && !(i.id.toLowerCase().includes(q) || (i.userId || "").toLowerCase().includes(q))) return false;
            if (status && i.status !== status) return false;
            if (severity && i.severity !== severity) return false;
            return true;
        });

        tbody.innerHTML = filtered.map(i => `
          <tr class="clickable" data-open="${i.id}">
            <td class="mono cell-primary">${i.id.slice(0, 8)}…</td>
            <td class="mono cell-muted">${(i.userId || "").slice(0, 8)}…</td>
            <td class="cell-muted">${i.type.replace(/_/g, " ")}</td>
            <td class="mono">${i.riskScore}</td>
            <td><span class="badge badge-${i.severity.toLowerCase()}">${i.severity}</span></td>
            <td><span class="badge badge-${i.status.toLowerCase()}">${i.status}</span></td>
            <td class="cell-muted">${new Date(i.createdAt).toLocaleString()}</td>
          </tr>`).join("") || '<tr><td colspan="7" class="cell-muted" style="text-align:center;padding:1.5rem;">No incidents match these filters.</td></tr>';

        tbody.querySelectorAll("[data-open]").forEach(row => {
            row.addEventListener("click", () => openDetail(row.getAttribute("data-open")));
        });
    }

    async function openDetail(id) {
        let incident;
        try {
            incident = await apiGet("/api/incidents/" + encodeURIComponent(id));
        } catch (error) {
            return;
        }

        const next = NEXT_ACTION[incident.status];
        detailPanel.style.display = "block";
        detailPanel.innerHTML = `
          <div style="display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:1rem;">
            <div>
              <div class="card-header" style="margin-bottom:.3rem;"><h2 class="mono">${incident.id.slice(0, 8)}\u2026</h2><span class="badge badge-${incident.severity.toLowerCase()}">${incident.severity}</span></div>
              <p class="text-muted" style="font-size:.85rem;">User ${incident.userId} · ${incident.type.replace(/_/g, " ")} · risk score ${incident.riskScore}</p>
              <p class="text-muted" style="font-size:.82rem;margin-top:.4rem;">${incident.description || ""}</p>
            </div>
            <div style="display:flex;gap:.5rem;">
              <a href="user-detail.html?id=${incident.userId}" class="btn btn-sm">View account</a>
              ${next ? `<button class="btn btn-sm btn-primary" id="advance-btn">${next.label}</button>` : '<span class="badge badge-neutral">Closed</span>'}
            </div>
          </div>
          <div class="section-title mt-lg">Status</div>
          <div class="step ${stepClass(incident.status, "OPEN")}"><div class="step-badge">${stepBadge(incident.status, "OPEN")}</div><div class="step-title">Open</div></div>
          <div class="step ${stepClass(incident.status, "INVESTIGATING")}"><div class="step-badge">${stepBadge(incident.status, "INVESTIGATING")}</div><div class="step-title">Investigating</div></div>
          <div class="step ${stepClass(incident.status, "CONTAINED")}"><div class="step-badge">${stepBadge(incident.status, "CONTAINED")}</div><div class="step-title">Contained</div></div>
          <div class="step ${stepClass(incident.status, "RECOVERED")}"><div class="step-badge">${stepBadge(incident.status, "RECOVERED")}</div><div class="step-title">Recovered</div></div>
          <div class="step ${stepClass(incident.status, "CLOSED")}"><div class="step-badge">${stepBadge(incident.status, "CLOSED")}</div><div class="step-title">Closed</div></div>
        `;

        const advanceBtn = document.getElementById("advance-btn");
        if (advanceBtn && next) {
            advanceBtn.addEventListener("click", async () => {
                try {
                    await apiPost("/api/incidents/" + encodeURIComponent(id) + "/" + next.action);
                    await loadIncidents();
                    openDetail(id);
                } catch (error) {
                    alert("Couldn't update the incident: " + error.message);
                }
            });
        }
    }

    const STAGES = ["OPEN", "INVESTIGATING", "CONTAINED", "RECOVERED", "CLOSED"];
    function stepClass(current, stage) {
        const ci = STAGES.indexOf(current), si = STAGES.indexOf(stage);
        return si < ci ? "done" : si === ci ? "current" : "";
    }
    function stepBadge(current, stage) {
        const ci = STAGES.indexOf(current), si = STAGES.indexOf(stage);
        return si < ci ? "&#10003;" : String(si + 1);
    }

    [searchInput, statusFilter, severityFilter].forEach(el => el.addEventListener("input", render));
    loadIncidents();
});
