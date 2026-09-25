# ATO Containment Service

**Account Takeover Containment and Recovery Service for Small SaaS Applications** (internship project).

## Purpose

Small SaaS companies rarely have a dedicated security team. When a customer account is hijacked, they
have no fast way to notice it, stop the damage, or safely hand the account back to its real owner. This
service is meant to:

1. **Detect** suspicious logins and stolen-token use.
2. **Contain** a compromised account (lock it, kill its sessions) so an attacker loses access quickly.
3. **Recover** the account by verifying the real owner and restoring access.
4. **Record** everything in a tenant-separated audit trail.

The full design — actors, architecture, module list, database design, API list, the detection/containment/
recovery workflow, and threat scenarios — is written up in [`docs/`](docs/), starting with
[`docs/README.md`](docs/README.md).

## Current state

The project has a working skeleton — a Spring Boot backend connected to MySQL, logging, and Postman-based
API testing — plus a full **visual prototype** of the product under `frontend/`, built with mock data so the
complete Detect → Investigate → Contain → Recover → Audit flow can be demonstrated end-to-end today.

The prototype's screens are **not wired to real backend logic yet** — no authentication, risk scoring, token
replay detection, or recovery logic exists on the server. Every account, session, incident, and log entry
you see in the prototype is sample data living in the browser (`localStorage`), so actions like revoking a
session or advancing a recovery step update the UI convincingly without touching the database. The real
implementation is designed in `docs/` and will replace this mock data module by module.

## Structure

```text
ato-containment-service/
├── docs/        Design documents (architecture, DB design, API list, workflow, threats)
├── backend/     Spring Boot (Java 17, Maven) REST API
├── frontend/    Visual prototype (HTML/CSS/JS, mock data) + the raw backend health-check page
├── database/    Local setup script + draft schema for upcoming features
├── tests/       Smoke test script + Postman collection
├── terraform/   Reserved for AWS infrastructure (later)
├── .env.example Example environment variables
├── README.md
└── .gitignore
```

Backend packages already scaffolded for upcoming work (currently near-empty): `risk/`, `incident/`,
`recovery/`, `logging/`, `security/`, alongside the active `controller/`, `service/`, `repository/`,
`model/`, `config/`, `exception/`.

## Tech stack

**In use now**
- Backend: Java 17, Spring Boot, Maven, Spring Web, Spring Data JPA
- Database: MySQL
- Frontend: plain HTML, CSS, JavaScript (no framework)
- API: REST (JSON), tested with Postman and a shell smoke-test script
- Logging: SLF4J + a request-logging filter, output to console and file

**Planned**
- Spring Security with JWT access tokens and rotating refresh tokens, for authentication and session management
- A rule-based risk-scoring engine for login and token-replay detection
- Incident management, account containment, and account recovery (email OTP and admin-assisted) modules
- Tenant isolation enforced at the query and schema level, with dedicated tests
- An append-only security audit log
- AWS deployment via Terraform (ECS/Fargate or Elastic Beanstalk, RDS for MySQL, S3/CloudFront for the
  frontend, SES for email, CloudWatch for logs, Secrets Manager for secrets) — see the AWS mapping table in
  [`docs/02-architecture-and-modules.md`](docs/02-architecture-and-modules.md)

## 1. Prerequisites

- **JDK 17 or newer**: check with `java -version`
- **Maven 3.9+**: check with `mvn -version`
- **MySQL 8.0+**: check with `mysql --version`
- **Python 3** (to serve the frontend) or the VS Code *Live Server* extension
- A modern browser

## 2. Configure MySQL

1. Make sure MySQL (or MariaDB) is running.
2. From the project root, create the local database and user (enter your MySQL root password when asked):

   ```bash
   mysql -u root -p < database/init.sql
   ```

   This creates database `ato_db` and user `ato_user` (password `ato_password`), scoped to `localhost`.
3. Verify the login works:

   ```bash
   mysql -u ato_user -p ato_db -e "SELECT 1;"
   ```

The backend reads these defaults from `backend/src/main/resources/application.properties`.
To use different values, set the environment variables `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

## 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API starts on **http://localhost:8080**. MySQL must be running first.
Run the unit test with `mvn test`.

## 4. Run the frontend

In a second terminal:

```bash
cd frontend
python -m http.server 5500      # use python3 on macOS/Linux if needed
```

Open **http://localhost:5500/login.html** for the prototype, or **http://localhost:5500/index.html** for the
raw backend health check. Serve it on port 5500 as shown; opening a file directly from disk will be blocked
by the browser.

## 5. Explore the security prototype

Sign in at `login.html` with any email and a password of 4+ characters (or use the pre-filled demo
credentials) — this is a mock sign-in, not real authentication. From there:

| Page | What it shows |
|---|---|
| Dashboard | Account/session/incident metrics, a recent-events timeline, and a risk-level breakdown |
| Users & Accounts | Every mock account across 3 tenants, filterable by tenant, status, and risk level |
| Account details | One account's risk score, signals, login history, sessions, and related incidents |
| Sessions | Every session across all accounts, with a working **Revoke** action |
| Risk Detection | Pick an account and see exactly which signals (new device, impossible travel, token replay, etc.) drove its risk score |
| Incidents | The incident queue with a status stepper (`OPEN → INVESTIGATING → CONTAINED → RECOVERED → CLOSED`) you can actually advance |
| Containment | Pick an account and fire the 5 containment actions (revoke sessions, lock account, restrict actions, notify user, create incident) |
| Recovery | Walk a contained account through the 7-step recovery flow, one "Advance" click at a time |
| Audit Logs | A searchable, filterable log of every simulated security event |

Use the **"Simulate suspicious login"** button in the top bar (visible on most pages) to inject a fresh
high-risk event into a random active account — it updates the dashboard, risk detection, incidents, and
audit log all at once, which is a good way to demonstrate the flow live.

**Important:** everything in the prototype is sample data stored in your browser's `localStorage`
(`assets/js/mock-data.js` seeds it). Nothing here calls the real backend or database — that's intentional for
this stage. Use **"Reset demo data"** at the bottom of the sidebar at any point to restore the original
sample state before a fresh demo run.

## 6. Test the health endpoint

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP","service":"ato-containment-service","database":"UP","timestamp":"2026-01-01T10:00:00.000Z"}
```

- `status: "DEGRADED"` with `database: "DOWN"` means the app runs but cannot reach MySQL.
- In the browser, `index.html` shows a green **Backend is running** status. Click **Check backend again** to re-test.
- Or run the smoke test: `./tests/health-check.sh`

On Windows PowerShell use `curl.exe` instead of `curl`.

## 7. Test with Postman

1. Open Postman → **Import** → select both files in `tests/postman/`:
   `ato-containment.postman_collection.json` and `ato-local.postman_environment.json`.
2. Select the **ATO Local** environment (top-right dropdown) — it sets `baseUrl` to `http://localhost:8080`.
3. With the backend running, open the **Health** folder and click **Send** on each request, or run the whole
   collection with **Run**. Both requests include automated checks (status code, response fields).

New endpoints should be added to this same collection as they're built.

## 8. Logging

Requests are logged to the console and to `backend/logs/ato-containment.log` (one line per request: method,
path, status, duration). Log levels are set in `application.properties`
(`logging.level.com.ato.containment=DEBUG`). Passwords, tokens and headers are never logged — see
`docs/06-threat-scenarios.md` (TS-8) for why that matters.

## 9. Design documents

Before adding new features, read [`docs/README.md`](docs/README.md) — it links the full design: actors,
architecture, the module list, database design, the planned API surface, the ATO detection/containment/
recovery workflow, and the threat scenarios those features are meant to stop.
