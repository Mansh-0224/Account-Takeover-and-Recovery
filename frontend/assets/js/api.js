/**
 * Small shared helper for calling the real backend. Every request includes
 * credentials so the browser sends/receives the session cookie the backend
 * sets at login (see AuthController) — that cookie is what the backend uses
 * to know which tenant a request belongs to.
 */
const API_BASE_URL = "http://localhost:8080";

async function apiFetch(path, options) {
    const response = await fetch(API_BASE_URL + path, Object.assign({ credentials: "include" }, options));
    if (!response.ok) {
        let message = "Request failed (HTTP " + response.status + ").";
        try {
            const body = await response.json();
            if (body && body.message) message = body.message;
        } catch (e) {
            // response had no JSON body; keep the generic message
        }
        const error = new Error(message);
        error.status = response.status;
        throw error;
    }
    if (response.status === 204) return null; // no content (e.g. DELETE)
    return response.json();
}

function apiGet(path) {
    return apiFetch(path, { method: "GET" });
}

function apiPost(path, body) {
    return apiFetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {}),
    });
}
