/**
 * Small shared helper for calling the real backend.
 *
 * Auth now works with a bearer token (not a cookie): after login, the token
 * the backend returns is kept in localStorage and sent as
 * "Authorization: Bearer <token>" on every request. A device id is also
 * generated once per browser and sent as "X-Device-Id", purely so sessions
 * are easier to tell apart on the Sessions page.
 */
const API_BASE_URL = "http://localhost:8080";
const SESSION_TOKEN_KEY = "aegis_session_token";
const DEVICE_ID_KEY = "aegis_device_id";

function getSessionToken() {
    return localStorage.getItem(SESSION_TOKEN_KEY);
}

function setSessionToken(token) {
    localStorage.setItem(SESSION_TOKEN_KEY, token);
}

function clearSessionToken() {
    localStorage.removeItem(SESSION_TOKEN_KEY);
}

function getDeviceId() {
    let id = localStorage.getItem(DEVICE_ID_KEY);
    if (!id) {
        id = (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2));
        localStorage.setItem(DEVICE_ID_KEY, id);
    }
    return id;
}

async function apiFetch(path, options) {
    options = options || {};
    const headers = Object.assign({}, options.headers, { "X-Device-Id": getDeviceId() });
    const token = getSessionToken();
    if (token) {
        headers["Authorization"] = "Bearer " + token;
    }

    const response = await fetch(API_BASE_URL + path, Object.assign({}, options, { headers }));

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
    const text = await response.text();
    return text ? JSON.parse(text) : null;
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

function apiPut(path, body) {
    return apiFetch(path, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {}),
    });
}

function apiDelete(path) {
    return apiFetch(path, { method: "DELETE" });
}
