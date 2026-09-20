# Tests

- **Unit tests** live with the backend code: run `mvn test` inside `backend/`.
- **Smoke test** (backend must be running): `./tests/health-check.sh`
  (on Windows, use Git Bash or run `curl.exe http://localhost:8080/api/health`).

Later phases will add API and end-to-end tests here.
