/**
 * Login page behavior. Calls the real backend (POST /api/auth/login).
 * On success the backend returns a session token, which is stored in
 * localStorage and sent as "Authorization: Bearer <token>" on every later
 * request (see api.js). Every other page calls GET /api/auth/me to find out
 * who is logged in — see session-guard.js.
 */
document.addEventListener("DOMContentLoaded", () => {
    if (getSessionToken()) {
        // Already signed in -- skip straight to the dashboard.
        window.location.href = "dashboard.html";
        return;
    }

    const form = document.getElementById("login-form");
    const errorBox = document.getElementById("login-error");
    const submitButton = document.getElementById("login-submit");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        errorBox.style.display = "none";
        submitButton.disabled = true;
        submitButton.textContent = "Signing in…";

        const email = document.getElementById("email").value.trim();
        const password = document.getElementById("password").value;

        try {
            const result = await apiPost("/api/auth/login", { email, password });
            setSessionToken(result.sessionToken);
            window.location.href = "dashboard.html";
        } catch (error) {
            // Show the backend's actual message when there is one -- a blocked
            // high-risk login and a plain wrong-password both return 401, but
            // with different, meaningful text (see AuthController#login).
            errorBox.textContent = error.status
                ? (error.message || "Incorrect email or password.")
                : "Couldn't reach the backend. Is it running on http://localhost:8080?";
            errorBox.style.display = "block";
            submitButton.disabled = false;
            submitButton.textContent = "Sign in";
        }
    });
});
