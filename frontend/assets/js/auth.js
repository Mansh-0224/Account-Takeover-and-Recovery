/**
 * Login page behavior. This calls the real backend (POST /api/auth/login).
 * On success the backend has set a session cookie, so every other page can
 * just call GET /api/auth/me to find out who is logged in — see session-guard.js.
 */
document.addEventListener("DOMContentLoaded", () => {
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
            await apiPost("/api/auth/login", { email, password });
            window.location.href = "dashboard.html";
        } catch (error) {
            errorBox.textContent = error.status === 401
                ? "Incorrect email or password."
                : "Couldn't reach the backend. Is it running on http://localhost:8080?";
            errorBox.style.display = "block";
            submitButton.disabled = false;
            submitButton.textContent = "Sign in";
        }
    });
});
