# Account Takeover Containment and Recovery Service

CERT-In internship project for detecting suspicious account activity, containing compromised accounts, and recovering access for small SaaS applications.

## Current implementation

The Java 17 / Spring Boot backend uses MySQL and bearer-token sessions. It includes tenant-scoped user management; BCrypt password hashing; session creation, listing, and revocation; login risk assessments and token-replay response; security-event search; incident lifecycle actions; automatic account containment; and a self-service recovery workflow. The frontend is plain HTML, CSS, and JavaScript and calls the backend for authentication, users, sessions, risk assessments, incidents, recovery, and security events.

Recovery codes currently have no email or SMS delivery provider. For safety, the API returns the same generic response for known and unknown email addresses and does not expose a code or request ID unless `APP_RECOVERY_EXPOSE_TEST_CODE=true` is explicitly set for local testing. Do not enable that setting outside a private development environment. AWS infrastructure and EBS snapshot recovery are not implemented. See [the API reference](docs/05-api-design.md) and [design documents](docs/README.md).

Some screens remain visual examples: the dashboard metrics, containment page, and account login-history section use sample data. The containment page is not wired to a manual containment endpoint; containment currently runs automatically for high-risk login or detected token replay.

## Requirements

- JDK 17 or newer
- Maven 3.9 or newer
- MySQL 8
- Python 3 or a local static-file server for the frontend

## Local setup

HEAD
### Authentication & sessions

- Passwords are hashed with BCrypt — never stored or logged in plain text.
- A session token is an opaque, cryptographically random value. The server only ever stores its SHA-256 hash
  (`sessions.token_hash`) — the raw token is shown to the client exactly once, in the login/register response.
- The token is sent as `Authorization: Bearer <token>` on every request; `CurrentSessionResolver` looks it up,
  checks it's `ACTIVE` and not expired (24h lifetime), and resolves the user/tenant/role from there.
- A non-`ACTIVE` account (`DISABLED`, `CONTAINED`, `RECOVERY_IN_PROGRESS`) cannot log in, even with the correct
  password.

### Security events, risk scoring & token replay 

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

1. Start MySQL. From the repository root, initialize the local database:
d352626 (Harden recovery access, fix tests, and update project docs)

   ```bash
   mysql -u root -p < database/init.sql
   ```

   This creates `ato_db` and the local development user in `database/init.sql`. Change those example credentials before using the database for anything beyond local development.

2. Start the backend:

   ```bash
   cd backend
   mvn spring-boot:run
   ```

   The API listens on `http://localhost:8080`. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the process environment to override the local defaults. Spring Boot does not load `.env` automatically; `.env.example` is a reference.

3. To exercise recovery in the local demo, set `APP_RECOVERY_EXPOSE_TEST_CODE=true` before starting the backend. For PowerShell:

   ```powershell
   $env:APP_RECOVERY_EXPOSE_TEST_CODE = "true"
   mvn spring-boot:run
   ```

   Leave this unset for normal runs. The local verification provider does not send messages; without this opt-in, recovery cannot be completed until a delivery provider is added.

4. In another terminal, serve the frontend:

   ```bash
   cd frontend
   python -m http.server 5500
   ```

   Open `http://localhost:5500/login.html`. The backend allows the documented local frontend origins in `application.properties`.

The first run seeds demo users with the password `demo1234`; see `DataSeeder.java`. Use these accounts only with a disposable local database.

## API overview

Authenticated requests use `Authorization: Bearer <sessionToken>`. Tenant identity comes from the resolved session, not a client-supplied tenant ID.

| Area | Routes |
|---|---|
| Health | `GET /api/health` |
| Authentication | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout` (revokes a supplied valid token), `POST /api/auth/logout-all`, `GET /api/auth/me`, `PUT /api/auth/password` |
| Tenant | `GET /api/tenants/me` |
| Users | `GET /api/users`, `GET /api/users/{id}`, `POST /api/users`, `PUT /api/users/{id}`, `DELETE /api/users/{id}` |
| Sessions | `GET /api/sessions`, `DELETE /api/sessions/{id}`, `DELETE /api/sessions` |
| Risk and events | `GET /api/risk-assessments`, `GET /api/security-events` |
| Incidents | `GET /api/incidents`, `GET /api/incidents/{id}`, `POST /api/incidents/{id}/investigate`, `/contain`, `/recover`, `/close` |
| Recovery | `POST /api/recovery/request`, `GET /api/recovery/{id}/status`, `POST /api/recovery/{id}/verify`, `POST /api/recovery/{id}/complete`, `GET /api/recovery/admin?userId=...` |

All endpoints except health, registration, login, logout, and the self-service recovery flow require a valid bearer token. User administration, incident lifecycle changes, and recovery-history access require a tenant or security admin. List and filter parameters are documented in [docs/05-api-design.md](docs/05-api-design.md).

## Tests

Run the backend test suite from `backend/`:

```bash
mvn test
```

The tests cover authentication, session behavior, tenant isolation, risk scoring and replay detection, security events, incident and containment behavior, recovery, and controller responses. The separate shell health check in `tests/health-check.sh` requires a running backend. The Postman collection is in `tests/postman/`.

The latest local `mvn test` run completed on October 29, 2026: 87 tests passed, with no failures, errors, or skips. It ran on Java 24.0.2 using the project's Java 17 compilation target. MySQL-backed startup and the browser workflow were not exercised in that run.

## Project layout

- `backend/` — Spring Boot API and unit/controller tests
- `frontend/` — browser interface
- `database/` — local initialization and draft schema
- `docs/` — workflow, architecture, API, data model, and threat scenarios
- `tests/` — smoke check and Postman collection
- `terraform/` — reserved; no AWS resources are defined or deployed
