# 06. Threat Scenarios

Each scenario states the attack, how it's simulated locally, what should detect it, and the expected response.
These become the test plan for later phases (`tests/`).

## TS-1: Credential stuffing / brute force

- **Attack:** attacker tries many passwords for one account (leaked-password list).
- **Simulated by:** `POST /api/dev/simulate/credential-stuffing` (repeated failed logins).
- **Detected by:** failed-login-burst signal (5+ failures in 10 minutes) in Risk / ATO Detection.
- **Expected response:** the eventual successful login (if any) scores at least Suspicious and requires
  step-up. `LOGIN_FAILED` events accumulate in `login_events`.
- **Test:** simulate 6 failed logins then 1 correct one; assert step-up is required.

## TS-2: Password-spray from a new location, then real login

- **Attack:** attacker gets in from an unfamiliar device and country.
- **Simulated by:** a login event with a device hash and country not in `known_devices` / prior `login_events`.
- **Detected by:** new-device (+20) and new-country (+20) signals -> score 40 -> Suspicious -> step-up email OTP.
- **Expected response:** login is not allowed until the OTP (sent to the real owner's email) is entered.
- **Test:** login from a new device/country; assert `stepUpRequired: true` and no session is created yet.

## TS-3: Impossible travel

- **Attack:** stolen credentials are used from a second country minutes after a real login.
- **Simulated by:** `POST /api/dev/simulate/impossible-travel` (two logins, different countries, short gap).
- **Detected by:** impossible-travel signal (+40), usually combined with new-device/new-country -> High risk.
- **Expected response:** login denied, `HIGH_RISK_LOGIN` incident opened, account contained immediately.
- **Test:** simulate the two logins; assert the second is denied and an incident with severity `HIGH` exists.

## TS-4: Stolen refresh token replay

- **Attack:** attacker steals a refresh token (XSS, log leak, intercepted traffic) and uses it after the real
  user has already refreshed.
- **Simulated by:** `POST /api/dev/simulate/token-replay` (calls `/api/auth/refresh` with an already-used token).
- **Detected by:** Token Replay Detection: the token's `used_at` is already set.
- **Expected response:** the whole session (and its token family) is revoked, a `CRITICAL` `TOKEN_REPLAY`
  incident is opened, the account is contained. Both the attacker and the legitimate user are logged out.
- **Test:** refresh once (valid), refresh again with the same token (replay); assert `401`, session `REVOKED`,
  and a `TOKEN_REPLAY` incident with severity `CRITICAL`.

## TS-5: Session hijack via stolen access token

- **Attack:** attacker steals a short-lived JWT access token and calls the API with it.
- **Mitigation:** the 15-minute lifetime limits the exposure window. Because the JWT's `sid` claim must match
  an `ACTIVE` session, containing the account (TS-2/TS-3/TS-4) revokes the session and immediately invalidates
  the stolen JWT even before it naturally expires.
- **Test:** contain a user mid-session; assert the next API call with their still-unexpired access token is `401`.

## TS-6: Cross-tenant data access

- **Attack:** a user or admin from Tenant A tries to read or modify data belonging to Tenant B (by guessing
  or incrementing an id, or by a bug that forgets a `tenant_id` filter).
- **Mitigation:** Tenant Isolation. `tenant_id` always comes from the JWT; repository queries are always scoped
  by it; composite foreign keys in the schema also block it at the database level.
- **Expected response:** `404 Not Found` for a resource in another tenant (not `403`, to avoid confirming that
  the id exists at all). Logged as `CROSS_TENANT_ACCESS_DENIED`.
- **Test:** as a Tenant A admin, request a Tenant B incident/user id; assert `404`.

## TS-7: Recovery flow abuse

- **Attack:** attacker tries to trigger or brute-force account recovery to regain access after containment,
  or to enumerate which emails have accounts.
- **Mitigation:** `POST /api/recovery/request` always returns the same response regardless of whether the
  account exists; recovery codes expire in 15 minutes; 3 wrong attempts cancel the request; every step is
  logged (`RECOVERY_REQUESTED`, `RECOVERY_VERIFIED` / `RECOVERY_FAILED`).
- **Test:** request recovery for a non-existent email and an existing one; assert identical responses.
  Submit 3 wrong codes; assert the request becomes `FAILED`.

## TS-8: Admin abuse / insider risk

- **Attack:** a Tenant Admin (or a compromised admin account) misuses containment or recovery powers.
- **Mitigation:** every containment and recovery-approval action requires `performed_by`/`approved_by` and is
  written to `audit_logs`, which the application role can only `INSERT`/`SELECT`, never `UPDATE`/`DELETE`.
- **Test:** perform a manual containment; assert an audit log row exists with the acting admin's id and cannot
  be modified through the API.

## 7. Summary — signal to scenario map

| Scenario | Primary module | Primary signal / control |
|---|---|---|
| TS-1 Credential stuffing | Risk / ATO Detection | Failed-login burst |
| TS-2 New device/country | Risk / ATO Detection | New device + new country, step-up |
| TS-3 Impossible travel | Risk / ATO Detection | Impossible-travel signal |
| TS-4 Refresh token replay | Token Replay Detection | Used-token reuse |
| TS-5 Stolen access token | Session Management | Session-bound JWT, fast revocation |
| TS-6 Cross-tenant access | Tenant Isolation | `tenant_id` scoping, composite FKs |
| TS-7 Recovery abuse | Account Recovery | Generic responses, expiry, attempt limits |
| TS-8 Admin abuse | Security Logging | Append-only, attributed audit log |
