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

Right now the project has a working skeleton: a Spring Boot backend connected to MySQL, a small
frontend that checks the backend's health, logging, and Postman-based API testing. Authentication,
risk detection, containment, recovery, and AWS integration are designed in `docs/` but not implemented yet.

## Structure

```text
ato-containment-service/
├── docs/        Design documents (architecture, DB design, API list, workflow, threats)
├── backend/     Spring Boot (Java 17, Maven) REST API
├── frontend/    Plain HTML/CSS/JavaScript page
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

- `status: "DEGRADED"` with `database: "DOWN"` means the app runs but cannot reach MySQL.
- In the browser, the frontend shows a green **Backend is running** status. Click **Check backend again** to re-test.
- Or run the smoke test: `./tests/health-check.sh`

On Windows PowerShell use `curl.exe` instead of `curl`.

## 6. Test with Postman

1. Open Postman → **Import** → select both files in `tests/postman/`:
   `ato-containment.postman_collection.json` and `ato-local.postman_environment.json`.
2. Select the **ATO Local** environment (top-right dropdown) — it sets `baseUrl` to `http://localhost:8080`.
3. With the backend running, open the **Health** folder and click **Send** on each request, or run the whole
   collection with **Run**. Both requests include automated checks (status code, response fields).

New endpoints should be added to this same collection as they're built.

## 7. Logging

Requests are logged to the console and to `backend/logs/ato-containment.log` (one line per request: method,
path, status, duration). Log levels are set in `application.properties`
(`logging.level.com.ato.containment=DEBUG`). Passwords, tokens and headers are never logged — see
`docs/06-threat-scenarios.md` (TS-8) for why that matters.

## 8. Design documents

Before adding new features, read [`docs/README.md`](docs/README.md) — it links the full design: actors,
architecture, the module list, database design, the planned API surface, the ATO detection/containment/
recovery workflow, and the threat scenarios those features are meant to stop.
