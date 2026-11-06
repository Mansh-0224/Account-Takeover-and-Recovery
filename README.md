# Account Takeover Containment and Recovery Service

Project exploring account takeover detection, account containment, and account recovery for small SaaS applications with limited security staff.

## Project status and technology

The local application uses Java 17, Spring Boot 3.5, Maven, MySQL, and Spring Data JPA. Its browser interface is built with HTML, CSS, and JavaScript; it is not a separate frontend framework.

Implemented backend features include tenant-scoped accounts, bearer-token sessions, login risk scoring, token-replay response, database-backed security events, incident management, automatic account containment, and self-service recovery. Recovery delivery is local-only; there is no email or SMS provider. Medium-risk logins are flagged for step-up verification, but no challenge is implemented. AWS infrastructure, EBS snapshot recovery, refresh-token rotation, and a separate audit-log service are not implemented.

## Authentication, sessions, and tenant access

- Registration creates a new tenant and its first tenant admin. Passwords must be at least eight characters and are hashed with BCrypt.
- Successful login or registration creates a 256-bit random opaque bearer token. The server returns the raw token once and stores only its SHA-256 hash in the database. Tokens are not JWTs; there is no refresh-token endpoint.
- Authenticated API requests send `Authorization: Bearer <sessionToken>`. The server resolves the session, user, tenant, and role from the stored token hash, and checks that the session is active and unexpired. Sessions expire after 24 hours.
- The browser frontend stores the bearer token in `localStorage`. This is suitable only for the local prototype; do not treat the frontend as production-hardened.
- Users can list and revoke their own sessions, log out of the current session, or log out everywhere. Password changes verify the current password, require a new password of at least eight characters, keep the current session, and revoke the user’s other sessions.
- Disabled, contained, and recovery-in-progress accounts cannot log in. Tenant identity is resolved from the session, not a tenant ID supplied by the caller. User records are tenant-scoped; a cross-tenant user ID returns 404. User changes, incident lifecycle actions, and recovery-history access require a tenant or security admin.

## Risk detection and security events

The rule-based risk engine (not machine learning) uses these signals:

- New `X-Device-Id` identifier: +20
- Previously unseen location value: +20. The value comes from the `X-Simulated-Location` request header; the application does not perform geo-IP lookup.
- Three or more accumulated failed logins: +20
- Password changed within the previous 10 minutes: +30
- Three or more existing active sessions: +25
- Session token reused from a different device or IP within 10 minutes: +50

Scores below 30 are LOW, 30–59 MEDIUM, and 60 or higher HIGH. LOW logins proceed. MEDIUM logins proceed with `stepUpRequired` flagged in the response; no challenge is enforced. HIGH logins are blocked and trigger account containment. A token-replay detection also revokes the affected session and contains the account. There is a limitation in the current replay flow: session resolution updates `lastSeen` before the replay detector checks the 10-minute window, effectively measuring from the current request. A device/IP mismatch can therefore be treated as replay even when the previous use was older than 10 minutes. The existing unit tests do not verify this integrated call order; do not rely on the 10-minute window until it is corrected.

The security-event types recorded by the application are `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `SESSION_CREATED`, `SESSION_REVOKED`, `PASSWORD_CHANGED`, `RECOVERY_STARTED`, `RECOVERY_FAILED`, `RECOVERY_COMPLETED`, `ATO_DETECTED`, `ACCOUNT_CONTAINED`, and `TOKEN_REPLAY`. `GET /api/security-events` is scoped to the caller’s tenant and supports `userId`, `eventType`, `severity`, `from`, and `to` filters, plus pagination (`page` defaults to 0; `size` defaults to 25 and is capped at 100). The Audit Logs page displays these security events; it is not backed by a separate audit-log table.

Request logging records the method, request path, response status, and duration. It omits headers, query strings, and request bodies. Passwords and raw session tokens are not written by the request-logging filter.

## Incidents, containment, and recovery

Incidents are database-backed. Allowed lifecycle transitions are `OPEN → INVESTIGATING`, `OPEN → CONTAINED`, `INVESTIGATING → CONTAINED`, `CONTAINED → RECOVERED`, and `RECOVERED → CLOSED`. High-risk login and token-replay detection automatically revoke active sessions, set the account to `CONTAINED`, record security events, and open or update an incident. Recovery completion moves contained incidents for that account to `RECOVERED`.

The incident endpoint `POST /api/incidents/{id}/contain` changes the **incident status**; it does not manually contain the account. There is no API to manually contain or release an account. The Containment page’s action buttons are sample controls and do not perform containment.

Self-service recovery uses a six-digit code tied to a UUID recovery-request ID. The code is stored as a SHA-256 hash, expires after 15 minutes, and five incorrect verification attempts fail that request and record a recovery-abuse incident. The five-attempt limit is per request; starting a newer request expires an older pending request, and no request-rate limiter is configured. Verification is idempotent after success; a completed request cannot be completed again. Completion requires a verified, unexpired request and a new password of at least eight characters. It activates the account, clears its failed-login count, revokes all existing sessions, updates contained incidents, records the recovery event, and creates a new session. Recovery history is restricted to tenant/security admins and the caller’s tenant.

No email or SMS delivery provider or recovery-request rate limiter is configured. A valid request immediately changes the account to `RECOVERY_IN_PROGRESS`. By default, `APP_RECOVERY_EXPOSE_TEST_CODE` is `false`, so `/api/recovery/request` returns the same generic response for matching and non-matching emails and includes neither a request ID nor a code. Since the local verification provider does not send messages, recovery cannot be completed in this default configuration and the account remains unable to log in until recovery is completed. If `APP_RECOVERY_EXPOSE_TEST_CODE=true`, a request for a matching, non-disabled account returns its request ID and local test code; unknown and disabled accounts still receive the generic response. This opt-in reveals account existence and must only be used on a private local development machine, never in a public or production environment. Treat a recovery-request ID like a bearer credential.

## Frontend coverage

The login and registration forms call the backend. The main Users, Sessions, Risk Detection, Incidents, Recovery, and Audit Logs pages also load backend data. The self-service forgot-password flow uses the recovery API. On the account detail page, the profile and latest risk assessment are live, but its login-history section **and embedded Active sessions panel** are sample data. Dashboard content, including its metrics, and the Containment page are visual examples; the decorative panels on the login and registration pages are static as well.

## Requirements

- JDK 17 or newer
- Maven 3.9 or newer
- MySQL 8
- Python 3 or another local static-file server for the frontend
- A modern browser

## Local setup

1. Start MySQL. From the repository root, initialize the local database and development user:

   ```bash
   mysql -u root -p < database/init.sql
   ```

   In Windows PowerShell, run through Command Prompt: `cmd /c "mysql -u root -p < database/init.sql"`. The script creates `ato_db` and the sample local user `ato_user` with password `ato_password`. These credentials are for a disposable local database only; change them before any broader use.

2. Start the backend from the `backend/` directory:

   ```bash
   mvn spring-boot:run
   ```

   The API listens on `http://localhost:8080`. Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as process environment variables when overriding the local defaults in `application.properties`. Spring Boot does not load `.env` automatically; `.env.example` lists the variables.

   For normal use, start it with the command above. To exercise self-service recovery locally, use this startup command instead, setting the opt-in before Spring Boot starts. In PowerShell, from the project root:

   ```powershell
   $env:APP_RECOVERY_EXPOSE_TEST_CODE = "true"
   cd backend
   mvn spring-boot:run
   ```

   Omit the environment-variable line for normal runs. Never enable test-code exposure on a public host.

3. In another terminal, serve the frontend:

   ```bash
   cd frontend
   python -m http.server 5500
   ```

   Open `http://localhost:5500/login.html`. The backend’s default CORS configuration permits this origin. `http://localhost:5500/index.html` checks the backend and database health.

On an empty database, `DataSeeder` creates sample tenants and users; all seeded users use `demo1234`. Examples are `priya.nair@northwind.io` (Northwind tenant admin), `maria.f@brightfinance.com` (Bright Finance tenant admin), and `security.admin@aegis.dev` (security admin). Other seeded users are listed in `backend/src/main/java/com/ato/containment/config/DataSeeder.java`. Use these accounts only with a disposable local database.

Hibernate uses `ddl-auto=update` to create or update entity tables. The project does not yet use database migrations, and the local configuration and seed credentials are not production-ready.

## API and design references

Authenticated requests use `Authorization: Bearer <sessionToken>`. The route list, roles, request filters, and response behavior are in the [API reference](docs/05-api-design.md). Health, registration, login, logout, and the public self-service recovery routes do not require a valid bearer token. Logout revokes a supplied valid token when present; other protected routes require authentication, with the administrative actions described above restricted by role.

| Area | Main routes |
|---|---|
| Health | `GET /api/health` |
| Authentication | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/logout-all`, `GET /api/auth/me`, `PUT /api/auth/password` |
| Tenant and users | `GET /api/tenants/me`; `GET/POST /api/users`, `GET/PUT/DELETE /api/users/{id}` |
| Sessions | `GET /api/sessions`, `DELETE /api/sessions/{id}`, `DELETE /api/sessions` |
| Risk and security events | `GET /api/risk-assessments`, `GET /api/security-events` |
| Incidents | `GET /api/incidents`, `GET /api/incidents/{id}`, `POST /api/incidents/{id}/investigate`, `POST /api/incidents/{id}/contain`, `POST /api/incidents/{id}/recover`, `POST /api/incidents/{id}/close` |
| Recovery | `POST /api/recovery/request`, `GET /api/recovery/{id}/status`, `POST /api/recovery/{id}/verify`, `POST /api/recovery/{id}/complete`, `GET /api/recovery/admin?userId=...` |

Project design documents:

- [Documentation index](docs/README.md)
- [Actors and scope](docs/01-actors-and-scope.md)
- [Architecture and modules](docs/02-architecture-and-modules.md)
- [ATO workflow](docs/03-ato-workflow.md)
- [Database design](docs/04-database-design.md)
- [API reference](docs/05-api-design.md)
- [Threat scenarios](docs/06-threat-scenarios.md)
- [Terraform status](terraform/README.md)

The database design document is a design draft; `database/schema_draft.sql` is not the runtime schema. Terraform contains no AWS resources. EBS snapshot recovery and AWS deployment are not implemented.

## Testing and manual checks

Run the backend unit and controller test suite from `backend/`:

```bash
mvn test
```

The latest recorded Surefire reports are from October 29, 2026: 87 tests, 0 failures, 0 errors, and 0 skipped. That run used Java 24.0.2 with the project’s Java 17 compilation target. It did not verify startup against MySQL or the browser workflow.

With the backend running, call `curl http://localhost:8080/api/health` or run `tests/health-check.sh`. On Windows, use Git Bash for the script or run `curl.exe http://localhost:8080/api/health`. The Postman collection and **ATO Local** environment are in `tests/postman/`; import both and select that environment. The collection covers health, authentication, users/tenant isolation, sessions, security events, and risk assessments; it does not cover the later incident or recovery routes.

To check tenant isolation manually, log in as a user in one tenant, use the returned session token with `GET /api/users`, then request a user ID from another tenant. That cross-tenant lookup should return 404. Automated service and controller tests cover tenant isolation, including ignoring a client-supplied `tenantId` query parameter.

## Logging and project layout

Application logs go to the console and `backend/logs/ato-containment.log` when the backend is launched from `backend/`. Request logs omit headers, query strings, and request bodies.

- `backend/` — Spring Boot API, JPA entities, services, controllers, and tests
- `frontend/` — HTML, CSS, and JavaScript interface
- `database/` — local database initialization and draft schema
- `docs/` — architecture, workflow, database, API, and threat documentation
- `tests/` — health check and Postman collection
- `terraform/` — reserved; no AWS resources are defined or deployed
