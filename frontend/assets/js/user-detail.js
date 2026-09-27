/**
 * Reads ?id= from the URL and fetches that one user from the real backend.
 * If the id belongs to a different tenant than the logged-in session, the
 * backend returns 404 — this page just shows that as "account not found",
 * which is exactly the point: it should look identical to an id that never
 * existed at all (see docs/06-threat-scenarios.md, TS-6).
 *
 * Only this header + "Account details" card are real. The risk score,
 * signals, login history, and sessions sections further down are still
 * sample placeholders — those backend modules don't exist yet.
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
    } catch (error) {
        showError(error.status === 404
            ? "No account found for this id. It may belong to a different tenant, or the link is out of date."
            : "Couldn\u2019t load this account from the backend.");
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
