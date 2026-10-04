/**
 * Loads real security events from GET /api/security-events, tenant-scoped
 * server-side. The event-type and severity dropdowns re-query the backend;
 * the free-text search box filters the already-loaded rows client-side.
 */
document.addEventListener("DOMContentLoaded", () => {
    const tbody = document.getElementById("audit-table-body");
    const searchInput = document.getElementById("search-input");
    const eventTypeFilter = document.getElementById("event-type-filter");
    const severityFilter = document.getElementById("severity-filter");
    const countLabel = document.getElementById("event-count");

    let loadedEvents = [];

    async function load() {
        tbody.innerHTML = '<tr><td colspan="7" class="cell-muted" style="text-align:center;padding:1.5rem;">Loading events from the backend…</td></tr>';
        const params = new URLSearchParams({ size: "100" });
        if (eventTypeFilter.value) params.set("eventType", eventTypeFilter.value);
        if (severityFilter.value) params.set("severity", severityFilter.value);

        try {
            const page = await apiGet("/api/security-events?" + params.toString());
            loadedEvents = page.content || [];
            render();
        } catch (error) {
            tbody.innerHTML = '<tr><td colspan="7" class="cell-muted" style="text-align:center;padding:1.5rem;color:var(--high);">Couldn\u2019t load events from the backend.</td></tr>';
        }
    }

    function render() {
        const q = searchInput.value.trim().toLowerCase();
        const filtered = loadedEvents.filter(e => {
            if (!q) return true;
            const haystack = [e.eventType, e.userId, e.ip, e.description].join(" ").toLowerCase();
            return haystack.includes(q);
        });

        countLabel.textContent = filtered.length + " event" + (filtered.length === 1 ? "" : "s");

        tbody.innerHTML = filtered.map(e => `
          <tr>
            <td class="mono cell-muted">${new Date(e.timestamp).toLocaleString()}</td>
            <td class="cell-primary">${escapeHtmlAudit(e.eventType.replace(/_/g, " "))}</td>
            <td class="mono cell-muted">${e.userId ? escapeHtmlAudit(e.userId.slice(0, 8)) + "…" : "\u2014"}</td>
            <td class="mono cell-muted">${escapeHtmlAudit(e.ip || "\u2014")}</td>
            <td class="cell-muted">${escapeHtmlAudit(e.device || "\u2014")}</td>
            <td><span class="badge badge-${e.severity.toLowerCase()}">${e.severity}</span></td>
            <td class="cell-muted" style="max-width:320px;">${escapeHtmlAudit(e.description || "")}</td>
          </tr>`).join("") || '<tr><td colspan="7" class="cell-muted" style="text-align:center;padding:1.5rem;">No events match these filters.</td></tr>';
    }

    searchInput.addEventListener("input", render);
    eventTypeFilter.addEventListener("change", load);
    severityFilter.addEventListener("change", load);
    load();
});

function escapeHtmlAudit(str) {
    return String(str).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
