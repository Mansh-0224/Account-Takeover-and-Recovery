// Base URL of the Spring Boot backend. Change this if your backend runs elsewhere.
const API_BASE_URL = "http://localhost:8080";
const HEALTH_URL = `${API_BASE_URL}/api/health`;
const REQUEST_TIMEOUT_MS = 5000;

const panel = document.getElementById("status-panel");
const statusText = document.getElementById("status-text");
const details = document.getElementById("status-details");
const help = document.getElementById("status-help");
const button = document.getElementById("check-button");

function setState(state, message) {
    panel.dataset.state = state; // "checking" | "ok" | "degraded" | "down"
    statusText.textContent = message;
}

function showHelp(message) {
    help.textContent = message;
    help.hidden = false;
}

function showDetails(data) {
    document.getElementById("detail-service").textContent = data.service;
    document.getElementById("detail-database").textContent = data.database;
    document.getElementById("detail-time").textContent = new Date(data.timestamp).toLocaleTimeString();
    details.hidden = false;
}

async function checkHealth() {
    button.disabled = true;
    details.hidden = true;
    help.hidden = true;
    setState("checking", "Checking the backend…");

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
        const response = await fetch(HEALTH_URL, { signal: controller.signal });
        if (!response.ok) {
            throw new Error(`The backend answered with HTTP ${response.status}.`);
        }
        const data = await response.json();
        showDetails(data);

        if (data.status === "UP") {
            setState("ok", "Backend is running");
        } else {
            setState("degraded", "Backend is running, but the database is unreachable");
            showHelp("Make sure PostgreSQL is running and that the username, password and database name " +
                     "in backend/src/main/resources/application.properties are correct.");
        }
    } catch (error) {
        setState("down", "Can't reach the backend");
        showHelp(`Nothing answered at ${API_BASE_URL}. Start the backend with "mvn spring-boot:run" ` +
                 "in the backend folder, then check again. If it is already running, make sure the " +
                 "frontend is served from http://localhost:5500 (see the README).");
        console.error("Health check failed:", error);
    } finally {
        clearTimeout(timer);
        button.disabled = false;
    }
}

button.addEventListener("click", checkHealth);
checkHealth(); // check once when the page loads
