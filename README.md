# ATO Containment Service

**Account Takeover Containment and Recovery Service for Small SaaS Applications** (internship project).

- **Phase 0 — Planning & Design:** done. See [`docs/`](docs/) for actors, architecture, module list,
  database design, API list, ATO workflow and threat scenarios.
- **Phase 1 — Local Development Environment (this version):** backend, frontend and PostgreSQL running
  locally, connected through a health check, with logging and Postman-based API testing set up.
  No authentication, detection, containment, recovery, or AWS yet — those are Phase 2 onward
  (see the roadmap in [`docs/README.md`](docs/README.md)).

```text
ato-containment-service/
├── docs/        Phase 0 design documents (architecture, DB design, API list, workflow, threats)
├── backend/     Spring Boot (Java 17, Maven) REST API
├── frontend/    Plain HTML/CSS/JavaScript page
├── database/    Local setup script + draft schema for later phases
├── tests/       Smoke test script + Postman collection
├── terraform/   Reserved for AWS (later)
├── .env.example Example environment variables
├── README.md
└── .gitignore
```

Backend packages already scaffolded for later phases (currently near-empty): `risk/`, `incident/`,
`recovery/`, `logging/`, `security/`, alongside the active `controller/`, `service/`, `repository/`,
`model/`, `config/`, `exception/`.

## 1. Prerequisites

- **JDK 17 or newer**: check with `java -version`
- **Maven 3.9+**: check with `mvn -version`
- **PostgreSQL 14+**: check with `psql --version`
- **Python 3** (to serve the frontend) or the VS Code *Live Server* extension
- A modern browser

## 2. Configure PostgreSQL

1. Make sure PostgreSQL is running.
2. From the project root, create the local user and database (enter the `postgres` password when asked):

   ```bash
   psql -U postgres -f database/init.sql
   ```

   This creates user `ato_user` (password `ato_password`) and database `ato_db`.
3. Verify the login works:

   ```bash
   psql -U ato_user -d ato_db -h localhost -c "SELECT 1;"
   ```

The backend reads these defaults from `backend/src/main/resources/application.properties`.
To use different values, set the environment variables `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

## 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API starts on **http://localhost:8080**. PostgreSQL must be running first.
Run the unit test with `mvn test`.

## 4. Run the frontend

In a second terminal:

```bash
cd frontend
python -m http.server 5500      # use python3 on macOS/Linux if needed
```

Open **http://localhost:5500**. (Or right-click `index.html` in VS Code and choose *Open with Live Server*.)
Serve it on port 5500 as shown; opening the file directly from disk will be blocked by the browser.

## 5. Test the health endpoint

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP","service":"ato-containment-service","database":"UP","timestamp":"2026-01-01T10:00:00.000Z"}
```

- `status: "DEGRADED"` with `database: "DOWN"` means the app runs but cannot reach PostgreSQL.
- In the browser, the frontend shows a green **Backend is running** status. Click **Check backend again** to re-test.
- Or run the smoke test: `./tests/health-check.sh`

On Windows PowerShell use `curl.exe` instead of `curl`.

## 6. Test with Postman

1. Open Postman → **Import** → select both files in `tests/postman/`:
   `ato-containment.postman_collection.json` and `ato-local.postman_environment.json`.
2. Select the **ATO Local** environment (top-right dropdown) — it sets `baseUrl` to `http://localhost:8080`.
3. With the backend running, open the **Health** folder and click **Send** on each request, or run the whole
   collection with **Run**. Both requests include automated checks (status code, response fields).

New endpoints from later phases should be added to this same collection.

## 7. Logging

Requests are logged to the console and to `backend/logs/ato-containment.log` (one line per request: method,
path, status, duration). Log levels are set in `application.properties`
(`logging.level.com.ato.containment=DEBUG`). Passwords, tokens and headers are never logged — see
`docs/06-threat-scenarios.md` (TS-8) for why that matters.

## 8. Design documents

Before touching later-phase code, read [`docs/README.md`](docs/README.md) — it links the full Phase 0 design:
actors, architecture, the module list, database design, the planned API surface, the ATO detection/containment/
recovery workflow, and the threat scenarios those features are meant to stop.
