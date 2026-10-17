/**
 * Drives the real request -> verify -> complete recovery flow against the
 * public /api/recovery/* endpoints (no login required for any of these).
 */
document.addEventListener("DOMContentLoaded", () => {
    const errorBox = document.getElementById("error-box");
    const stepRequest = document.getElementById("step-request");
    const stepVerify = document.getElementById("step-verify");
    const stepComplete = document.getElementById("step-complete");

    let requestId = null;

    function showError(message) {
        errorBox.textContent = message;
        errorBox.style.display = "block";
    }
    function clearError() {
        errorBox.style.display = "none";
    }

    document.getElementById("request-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        clearError();
        const btn = document.getElementById("request-submit");
        btn.disabled = true;
        try {
            const result = await apiPost("/api/recovery/request", { email: document.getElementById("forgot-email").value.trim() });
            if (!result.requestId) {
                showError(result.message || "If that email has an account, recovery instructions have been sent.");
                btn.disabled = false;
                return;
            }
            requestId = result.requestId;
            if (result.testModeCode) {
                const hint = document.getElementById("test-mode-hint");
                hint.style.display = "block";
                hint.innerHTML = "<strong>Test mode:</strong> your code is <code>" + result.testModeCode +
                    "</code> (a real deployment would email this instead of showing it here).";
            }
            stepRequest.style.display = "none";
            stepVerify.style.display = "block";
        } catch (error) {
            showError(error.message || "Couldn't start recovery.");
            btn.disabled = false;
        }
    });

    document.getElementById("verify-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        clearError();
        const btn = document.getElementById("verify-submit");
        btn.disabled = true;
        try {
            await apiPost("/api/recovery/" + encodeURIComponent(requestId) + "/verify",
                { code: document.getElementById("verify-code").value.trim() });
            stepVerify.style.display = "none";
            stepComplete.style.display = "block";
        } catch (error) {
            showError(error.message || "Verification failed.");
            btn.disabled = false;
        }
    });

    document.getElementById("complete-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        clearError();
        const btn = document.getElementById("complete-submit");
        btn.disabled = true;
        try {
            const result = await apiPost("/api/recovery/" + encodeURIComponent(requestId) + "/complete",
                { newPassword: document.getElementById("new-password").value });
            setSessionToken(result.sessionToken); // new trusted session, created only now
            window.location.href = "dashboard.html";
        } catch (error) {
            showError(error.message || "Couldn't complete recovery.");
            btn.disabled = false;
        }
    });
});
