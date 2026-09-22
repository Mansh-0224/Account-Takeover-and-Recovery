# Tests

- **Unit tests** live with the backend code: run `mvn test` inside `backend/`.
- **Smoke test** (backend must be running): `./tests/health-check.sh`
  (on Windows, use Git Bash or run `curl.exe http://localhost:8080/api/health`).
- **Postman**: import both files from `tests/postman/`
  (`ato-containment.postman_collection.json` and `ato-local.postman_environment.json`),
  select the **ATO Local** environment, then run the collection.
  Add new requests here as new endpoints are built.

Later phases will add API and end-to-end tests (including the attacker simulations from
`docs/06-threat-scenarios.md`).
