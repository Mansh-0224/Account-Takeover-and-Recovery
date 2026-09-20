# ATO Containment Service

**Account Takeover Containment and Recovery Service for Small SaaS Applications** (internship project).

**Phase 1 (this version):** project skeleton, PostgreSQL connection, and a health check.
No authentication, detection, containment, recovery, or AWS yet.

```text
ato-containment-service/
├── backend/     Spring Boot (Java 17, Maven) REST API
├── frontend/    Plain HTML/CSS/JavaScript page
├── database/    SQL setup script for local PostgreSQL
├── tests/       Smoke test script
├── terraform/   Reserved for AWS (later)
├── README.md
└── .gitignore
```

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
