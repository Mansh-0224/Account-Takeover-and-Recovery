# API reference

The backend serves JSON under `/api`. Timestamps use ISO-8601. Authenticated routes require `Authorization: Bearer <sessionToken>`. Tenant scope is resolved from the database-backed session; clients cannot select a tenant by query parameter or request body. There is no general Spring Security filter chain: controllers call `CurrentSessionResolver` for authenticated routes.

## Routes

| Method | Path | Access | Behavior |
|---|---|---|---|
| GET | `/api/health` | Public | Application and database health |
| POST | `/api/auth/register` | Public | Create a tenant and its first tenant admin, then start a session |
| POST | `/api/auth/login` | Public | Check credentials, assess risk, and create a session when allowed |
| POST | `/api/auth/logout` | Optional bearer token | Revoke the current session if the supplied token is valid |
| POST | `/api/auth/logout-all` | Bearer token | Revoke all sessions for the caller |
| GET | `/api/auth/me` | Bearer token | Return the caller's user profile |
| PUT | `/api/auth/password` | Bearer token | Change password and revoke other sessions |
| GET | `/api/tenants/me` | Bearer token | Return the caller's tenant |
| GET | `/api/users` | Bearer token | List users in the caller's tenant |
| GET | `/api/users/{id}` | Bearer token | Get a same-tenant user; other-tenant IDs return 404 |
| POST | `/api/users` | Tenant/security admin | Create a user in the caller's tenant |
| PUT | `/api/users/{id}` | Tenant/security admin | Update a same-tenant user |
| DELETE | `/api/users/{id}` | Tenant/security admin | Delete a same-tenant user |
| GET | `/api/sessions` | Bearer token | List the caller's sessions |
| DELETE | `/api/sessions/{id}` | Bearer token | Revoke one of the caller's sessions |
| DELETE | `/api/sessions` | Bearer token | Revoke all of the caller's sessions |
| GET | `/api/risk-assessments` | Bearer token | List tenant risk assessments; optional `userId` filter |
| GET | `/api/security-events` | Bearer token | Search tenant events; filters: `userId`, `eventType`, `severity`, `from`, `to`, `page`, `size` |
| GET | `/api/incidents` | Bearer token | List tenant incidents |
| GET | `/api/incidents/{id}` | Bearer token | Get a same-tenant incident |
| POST | `/api/incidents/{id}/investigate` | Tenant/security admin | Move an open incident to investigation |
| POST | `/api/incidents/{id}/contain` | Tenant/security admin | Mark an incident contained |
| POST | `/api/incidents/{id}/recover` | Tenant/security admin | Mark an incident recovered |
| POST | `/api/incidents/{id}/close` | Tenant/security admin | Close a recovered incident |
| POST | `/api/recovery/request` | Public | Start recovery; response is generic unless local test-code exposure is enabled |
| GET | `/api/recovery/{id}/status` | Public | Read status for a recovery request ID |
| POST | `/api/recovery/{id}/verify` | Public | Verify the recovery code |
| POST | `/api/recovery/{id}/complete` | Public | Set a new password after verification and create a session |
| GET | `/api/recovery/admin?userId={id}` | Tenant/security admin | Read same-tenant recovery history |

The `recover` incident action marks an incident as recovered; it is not itself the account recovery process. The account owner completes recovery through the four public recovery routes. Containment is invoked automatically by high-risk login and token-replay handling; there is no manual user-containment endpoint yet.

## Risk and recovery behavior

The risk engine uses six rules: new device, unusual location, repeated failed logins, recent password change, multiple active sessions, and token replay. Scores below 30 are LOW, 30–59 MEDIUM, and 60 or more HIGH. LOW logins proceed. MEDIUM logins proceed with `stepUpRequired` flagged, but no challenge channel is implemented. HIGH risk blocks login and contains the account. A token replay also revokes the session and contains the account.

Recovery codes are hashed in the database and expire after 15 minutes; five incorrect attempts fail a request. The local provider does not deliver messages. Set `APP_RECOVERY_EXPOSE_TEST_CODE=true` only for private local testing to return the request ID and code to the caller. Keep it unset elsewhere. A real email/SMS provider and production recovery delivery remain to be implemented.

## Response conventions and limits

Errors use the shared JSON error response. Security-event search is paged; page defaults to 0 and size to 25, capped at 100. Other list routes return arrays. See the controller and DTO classes for request and response fields; request validation is not yet consistently implemented across every endpoint.

## Not implemented

The following routes in the original design are not present: step-up verification, refresh-token rotation, tenant risk-setting management, incident audit-trail and containment-action history, manual account release/containment, and admin-approved recovery. AWS hosting and EBS snapshot recovery are also not implemented.
