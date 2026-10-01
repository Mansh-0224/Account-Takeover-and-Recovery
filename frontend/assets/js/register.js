/**
 * Registration page behavior. Calls POST /api/auth/register, which creates a
 * brand-new tenant and the caller as its first admin, then logs them straight
 * in (same response shape as /api/auth/login).
 */
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("register-form");
    const errorBox = document.getElementById("register-error");
    const submitButton = document.getElementById("register-submit");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        errorBox.style.display = "none";
        submitButton.disabled = true;
        submitButton.textContent = "Creating account…";

        const companyName = document.getElementById("companyName").value.trim();
        const fullName = document.getElementById("fullName").value.trim();
        const email = document.getElementById("email").value.trim();
        const password = document.getElementById("password").value;

        try {
            const result = await apiPost("/api/auth/register", { companyName, fullName, email, password });
            setSessionToken(result.sessionToken);
            window.location.href = "dashboard.html";
        } catch (error) {
            errorBox.textContent = error.message || "Couldn't create the account.";
            errorBox.style.display = "block";
            submitButton.disabled = false;
            submitButton.textContent = "Create account";
        }
    });
});
