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

The project has a working Spring Boot backend connected to MySQL, with **real, tenant-isolated authentication
and session management** — not mock data. `Tenant`, `Role`, `User`, and `Session` are real JPA entities backed
by real tables. Logging in (or registering) creates a real, database-backed session identified by a bearer
token; every authenticated endpoint resolves "who is this and which tenant do they belong to" from that
session, never from anything the client claims.

The full **visual prototype** under `frontend/` still demonstrates the complete Detect → Investigate → Contain
→ Recover → Audit flow. **Users & Accounts**, **Account details**, **Sessions**, **Risk Detection**, and **Audit
Logs** now show real backend data, plus a **Create an account** page (reachable from login) for registration.
**Incidents**, **Containment**, and **Recovery** still use sample data, since those modules aren't built yet —
each is unchanged from the prototype. The real implementation plan lives in `docs/` and will replace the
remaining mock data module by module.

### Authentication & sessions (Phase 3)

- Passwords are hashed with BCrypt — never stored or logged in plain text.
- A session token is an opaque, cryptographically random value. The server only ever stores its SHA-256 hash
  (`sessions.token_hash`) — the raw token is shown to the client exactly once, in the login/register response.
- The token is sent as `Authorization: Bearer <token>` on every request; `CurrentSessionResolver` looks it up,
  checks it's `ACTIVE` and not expired (24h lifetime), and resolves the user/tenant/role from there.
- A non-`ACTIVE` account (`DISABLED`, `CONTAINED`, `RECOVERY_IN_PROGRESS`) cannot log in, even with the correct
  password.

### Security events, risk scoring & token replay (Phase 4)

- Every login, session, and password-change event is written to an append-only `security_events` table —
  10 event types, searchable by type/severity/user/date range via `GET /api/security-events`. Three of those
  types (`RECOVERY_STARTED/FAILED/COMPLETED`) exist in the schema but nothing triggers them yet — no recovery
  module is built.
- `risk/RiskEngine` scores every login with 6 rule-based signals (no ML), stored as a `RiskAssessment`:
  new device +20, unusual location +20, multiple failed logins +20, recent password change +30, many active
  sessions +25, token replay +50 — classified LOW (0–29) / MEDIUM (30–59) / HIGH (60+).
- The login flow applies that score: **LOW** allows normally, **MEDIUM** allows through but is clearly flagged
  as needing step-up verification (no real OTP/challenge channel exists, so this is never silently hidden),
  **HIGH** blocks the login and automatically contains the account.
- `risk/TokenReplayDetector` flags a session token reused from a different device/IP within 10 minutes of last
  use, revokes that session, and contains the account. This project has no real JWTs, so the session's own id
  is used as the practical `jti` equivalent — documented in the class itself.
- Failed logins are tracked (`failedLoginCount`, `lastFailedLoginAt`) and feed the risk engine's
  MULTIPLE_FAILED_LOGINS signal.
- Sessions can be listed, revoked individually, or revoked all at once (`GET`/`DELETE /api/sessions`,
  `DELETE /api/sessions/{id}`); changing your password revokes every other session automatically.
- Nothing logs a password, token, OTP, or `Authorization` header — see `RequestLoggingFilter` and the
  `AuthService`/`SessionService` log lines, which log identifiers (user id, session id, IP) only.

### Tenant isolation

- A user's tenant is decided once, at login, from their database record — never from anything the client sends.
- It's then stored server-side in an `HttpSession` and read back on every request via `CurrentSession`.
- Every user lookup goes through `UserRepository.findByIdAndTenantId(id, tenantId)`. If an id belongs to a
  different tenant, the query finds nothing — the caller gets the same 404 as a truly nonexistent id, never a
  glimpse of another tenant's data.
- Proven in `backend/src/test/java/.../UserServiceTenantIsolationTest.java` (service layer) and
  `UserControllerTenantIsolationTest.java` (HTTP layer, including a test that a spoofed `tenantId` query
  parameter has no effect).

## Structure

```text
ato-containment-service/
├── docs/        Design documents (architecture, DB design, API list, workflow, threats)
├── backend/     Spring Boot (Java 17, Maven) REST API — tenants, roles, users, sessions
├── frontend/    Visual prototype (HTML/CSS/JS) — Users/Account details wired to real data, rest is mock
├── database/    Local setup script + draft schema for upcoming features
├── tests/       Smoke test script + Postman collection
├── terraform/   Reserved for AWS infrastructure (later)
├── .env.example Example environment variables
├── README.md
└── .gitignore
```

Backend packages: `model` (JPA entities), `repository` (Spring Data), `service` (business logic incl. tenant
isolation), `controller` (REST), `dto` (request/response shapes, never expose entities directly),
`security` (session-based tenant resolution), `config` (CORS, data seeding), `exception`. Still-empty
placeholders for upcoming work: `risk/`, `incident/`, `recovery/`, `logging/` (logging itself is implemented;
the folder is reserved for a future audit-log module).

## Tech stack

**In use now**
- Backend: Java 17, Spring Boot, Maven, Spring Web, Spring Data JPA
- Database: MySQL
- Auth: server-side sessions (`HttpSession`) set at login, read via a small `CurrentSession` helper — no JWTs
  yet, no `spring-boot-starter-security` (which would auto-lock every endpoint); just
  `spring-security-crypto` for BCrypt password hashing
- Frontend: plain HTML, CSS, JavaScript (no framework); two pages call the real API with `fetch`
- API: REST (JSON), tested with Postman and a shell smoke-test script
- Logging: SLF4J + a request-logging filter, output to console and file

**Planned**
- JWT access/refresh tokens and multi-device session management (replacing the current single `HttpSession`)
- A rule-based risk-scoring engine for login and token-replay detection
- Incident management, account containment, and account recovery (email OTP and admin-assisted) modules
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

## 5. Explore the prototype

Sign in at `login.html` with a **real seeded account** — this now calls the actual backend:

- `security.admin@aegis.dev` / `demo1234` (Security Admin, "Platform" tenant), or
- `priya.nair@northwind.io` / `demo1234` (Tenant Admin, "Northwind Retail"), or
- any other user listed in `backend/src/main/java/com/ato/containment/config/DataSeeder.java`

Every seeded account uses the password `demo1234`. From the dashboard:

| Page | What it shows |
|---|---|
| Dashboard | Account/session/incident metrics, a recent-events timeline, a risk-level breakdown — **sample data** |
| Users & Accounts | **Real data**: every user in your own tenant only, fetched from `GET /api/users` |
| Account details | **Partly real**: name/email/tenant/role/status/created date come from `GET /api/users/{id}`; risk score, signals, login history, and sessions further down the page are still sample data |
| Sessions, Risk Detection, Incidents, Containment, Recovery, Audit Logs | **Sample data** — unchanged from the earlier prototype; these backend modules don't exist yet |

Try logging in as a user from one tenant (e.g. `priya.nair@northwind.io`, Northwind Retail) and note that
**Users & Accounts only ever shows Northwind's users** — logging in as `maria.f@brightfinance.com` instead
shows only Bright Finance's users. That's tenant isolation working, not a bug.

## 6. Test tenant isolation

Automated tests (see "Run the backend" above — `mvn test`):

- `UserServiceTenantIsolationTest` — proves the repository/service layer denies a cross-tenant lookup
- `UserControllerTenantIsolationTest` — proves the same thing over HTTP (returns 404), and proves a
  client-supplied `tenantId` query parameter has no effect

To see it manually with `curl` (the login response includes a bearer token — grab it with `jq`, or just copy
it by hand from the response):

```bash
# Log in as a Northwind user and grab the session token
TOKEN=$(curl -s -H "Content-Type: application/json" \
  -d '{"email":"priya.nair@northwind.io","password":"demo1234"}' \
  http://localhost:8080/api/auth/login | jq -r .sessionToken)

# List users -- only Northwind's users come back
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users

# Copy a user id from a DIFFERENT tenant (e.g. a Bright Finance user) and try it:
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/<a-bright-finance-user-id>
# -> 404, even though that id genuinely exists -- just not in Priya's tenant
```

(No `jq`? Just run the login `curl` without the `TOKEN=`/`jq` part, copy the `sessionToken` value from the
printed JSON by hand, and paste it into the `Authorization: Bearer ...` header yourself.)

## 7. Test the health endpoint

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

## 8. Test with Postman

1. Open Postman → **Import** → select both files in `tests/postman/`:
   `ato-containment.postman_collection.json` and `ato-local.postman_environment.json`.
2. Select the **ATO Local** environment (top-right dropdown) — it sets `baseUrl` to `http://localhost:8080`.
3. With the backend running, open the **Health** folder and click **Send** on each request, or run the whole
   collection with **Run**. Both requests include automated checks (status code, response fields).

New endpoints should be added to this same collection as they're built.

## 9. Logging

Requests are logged to the console and to `backend/logs/ato-containment.log` (one line per request: method,
path, status, duration). Log levels are set in `application.properties`
(`logging.level.com.ato.containment=DEBUG`). Passwords, tokens and headers are never logged — see
`docs/06-threat-scenarios.md` (TS-8) for why that matters.

## 10. Design documents

Before adding new features, read [`docs/README.md`](docs/README.md) — it links the full design: actors,
architecture, the module list, database design, the planned API surface, the ATO detection/containment/
recovery workflow, and the threat scenarios those features are meant to stop.
