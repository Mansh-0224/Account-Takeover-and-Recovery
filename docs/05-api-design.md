# 05. API Design

## 1. Conventions

- Base path: `/api`. JSON in, JSON out. All timestamps are ISO-8601 UTC.
- Auth: `Authorization: Bearer <access token>`, except the public endpoints listed below.
- Tenant: taken from the JWT (`tenant_id` claim), **never** from the URL, query string or body.
- Errors: the shared `ErrorResponse` shape already used by `/api/health`:
  ```json
  { "timestamp": "...", "status": 404, "error": "Not Found", "message": "...", "path": "/api/..." }
  ```
- Pagination: `?page=0&size=20` on list endpoints; response wraps results as `{ "content": [...], "page": 0, "size": 20, "totalElements": 0 }`.
- Versioning: none yet. A `/api/v2` prefix can be introduced later if a breaking change is needed.
- Only `/api/health`, `/api/auth/*` and `/api/recovery/*` (the public parts) are unauthenticated. Everything else requires a valid access token.

## 2. Endpoint list

Only `GET /api/health` is implemented today. Everything else below is the planned design.

### Health — implemented

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | none | Backend and database status (implemented) |

### Authentication — Module 1

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | none | Create a tenant and its first admin user (or add a user to an existing tenant, depending on final design) |
| POST | `/api/auth/login` | none | Verify credentials, run risk evaluation, return tokens or a step-up challenge |
| POST | `/api/auth/step-up/verify` | none (uses a short-lived challenge id) | Submit the OTP for a suspicious login |
| POST | `/api/auth/refresh` | refresh token | Rotate the refresh token, return a new access token; detects replay |
| POST | `/api/auth/logout` | access token | Revoke the current session |

### Session management — Module 2

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/sessions/me` | user | List the caller's own active sessions |
| DELETE | `/api/sessions/me/{sessionId}` | user | Revoke one of the caller's own sessions ("log out this device") |
| GET | `/api/admin/users/{userId}/sessions` | tenant admin | List a user's sessions |
| DELETE | `/api/admin/users/{userId}/sessions` | tenant admin | Revoke all of a user's sessions |

### Risk / ATO detection — Module 3 (mostly internal)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/risk-assessments?userId=` | tenant admin | List recent risk assessments for a user |
| GET | `/api/admin/tenants/me/security-settings` | tenant admin | View current risk thresholds |
| PUT | `/api/admin/tenants/me/security-settings` | tenant admin | Update thresholds (bounded, audited) |

### Token replay detection — Module 4 (no dedicated endpoints; surfaced through incidents)

### Incident management — Module 5

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/incidents` | tenant admin / security admin | List incidents (filter by status, severity, user) |
| GET | `/api/admin/incidents/{id}` | tenant admin / security admin | Incident detail with its timeline |
| POST | `/api/admin/incidents/{id}/resolve` | tenant admin / security admin | Close an incident (e.g. false positive) |
| GET | `/api/admin/incidents/{id}/audit-trail` | tenant admin / security admin | Audit log entries linked to the incident |

### Account containment — Module 6

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/admin/users/{userId}/contain` | tenant admin / security admin | Manually contain a user |
| POST | `/api/admin/users/{userId}/release` | tenant admin / security admin | Release a user without full recovery (false positive) |
| GET | `/api/admin/incidents/{id}/containment-actions` | tenant admin / security admin | List containment actions for an incident |

### Account recovery — Module 7

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/recovery/request` | none | Start recovery for an email + tenant (always returns the same generic response) |
| POST | `/api/recovery/verify` | none (uses request id) | Submit the recovery code |
| POST | `/api/recovery/complete` | none (uses verified request id) | Set a new password and restore the account |
| GET | `/api/admin/recovery-requests?status=PENDING` | tenant admin | List recovery requests needing attention |
| POST | `/api/admin/recovery/{id}/approve` | tenant admin | Approve an admin-assisted recovery |

### Tenant isolation — Module 8 (no dedicated endpoints; enforced everywhere above, verified by tests)

### Security logging — Module 9

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/audit-logs?from=&to=&action=` | tenant admin (own tenant) / security admin (all) | Search the audit log |

### Attacker simulator — dev profile only, disabled outside `dev`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/dev/simulate/credential-stuffing` | dev profile | Fire repeated failed logins at an account |
| POST | `/api/dev/simulate/impossible-travel` | dev profile | Two logins for the same user from far-apart locations, close in time |
| POST | `/api/dev/simulate/token-replay` | dev profile | Reuse an already-rotated refresh token |

## 3. Sample payloads (for Postman, planned)

**POST /api/auth/login**
```json
{ "tenantSlug": "acme", "email": "user@example.com", "password": "correct horse battery staple" }
```
Response (normal): `{ "accessToken": "...", "refreshToken": "...", "expiresIn": 900 }`
Response (suspicious): `{ "stepUpRequired": true, "challengeId": "..." }`
Response (high risk): `HTTP 403 { "error": "ACCOUNT_CONTAINED", "message": "..." }`

**POST /api/admin/users/{userId}/contain**
```json
{ "reason": "Reported by user as unauthorized access" }
```

**POST /api/recovery/request**
```json
{ "tenantSlug": "acme", "email": "user@example.com" }
```
Always responds `200` with the same generic message, so the endpoint cannot be used to discover accounts.
