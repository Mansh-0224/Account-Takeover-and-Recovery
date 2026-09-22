# 01. Actors and Scope

## 1. Product summary

Small SaaS companies rarely have a security team. When a customer's account is hijacked, they have no fast way
to **detect** it, **stop the damage**, and **safely give the account back**. This service does that:

1. **Detect** suspicious logins and stolen-token use.
2. **Contain** the account (lock it, kill all sessions) so the attacker loses access.
3. **Recover** the account by verifying the real owner and restoring access.
4. **Record** everything in an audit trail, separated per tenant.

## 2. Design decisions and assumptions

| # | Decision | Reason |
|---|---|---|
| D1 | Single Spring Boot application (modular monolith) | Simple to build, run and explain in an internship |
| D2 | Shared database, shared schema, `tenant_id` on every row | Cheapest multi-tenancy model; isolation enforced in code and tests |
| D3 | The app includes a demo SaaS layer (users, login, sessions) | ATO protection needs real logins to protect |
| D4 | Email delivery is simulated (`notifications` table) | No SMTP or AWS SES needed locally |
| D5 | Geo-location and device come from request headers in the `dev` profile | Makes attacks easy to simulate; real geo-IP is optional later |
| D6 | Role-based access: `USER`, `TENANT_ADMIN`, `SECURITY_ADMIN` | Matches the actors below |
| D7 | The Security Admin belongs to a special `platform` tenant and can read across tenants (audited) | Keeps `users.tenant_id` always set |

## 3. Actors

| Actor | Type | Description | Typical goals |
|---|---|---|---|
| **SaaS User** | Human, role `USER` | Normal user of a tenant's application | Log in, manage own sessions, regain access after a takeover |
| **Tenant Admin** | Human, role `TENANT_ADMIN` | Manages users and security settings for **one** tenant | Add users, review incidents, contain or release users, approve recoveries |
| **Security Admin** | Human, role `SECURITY_ADMIN` | Platform-level monitor across all tenants | Watch incidents, review audit logs, tune default risk rules |
| **Attacker** | Simulated | Malicious outsider (never a real account role) | Take over accounts using stolen passwords or tokens |
| **System** | Automated | The risk engine and background jobs | Score logins, open incidents, contain accounts, expire tokens |

The **Attacker** exists only as a test tool: scripts and dev-profile simulator endpoints that perform the
attacks listed in `06-threat-scenarios.md`.

## 4. Permission matrix

| Capability | SaaS User | Tenant Admin | Security Admin |
|---|---|---|---|
| Log in, view own profile | Yes | Yes | Yes |
| View / revoke own sessions | Yes | Yes | Yes |
| Start own account recovery (public flow) | Yes | Yes | Yes |
| Create and list users | No | Own tenant | Read: all tenants |
| View incidents | No | Own tenant | All tenants |
| Manually contain / release a user | No | Own tenant | All tenants (audited) |
| Approve admin-assisted recovery | No | Own tenant | All tenants (audited) |
| Change risk thresholds | No | Own tenant | Platform defaults |
| View audit logs | No | Own tenant | All tenants |
| Use attacker simulator | dev profile only | dev profile only | dev profile only |

## 5. Scope

**In scope**
- Login risk scoring using simple rules (new device, new country, impossible travel, failed-login bursts, bad IP list)
- Refresh-token replay detection and session binding
- Incident lifecycle, containment actions, recovery flows, step-up verification (email OTP)
- Tenant isolation and tests for it
- Security audit log

**Out of scope (for now)**
- Real email/SMS delivery, real geo-IP database, authenticator-app MFA
- Machine-learning detection
- Phishing, malware or network-level DDoS protection
- Password-reset UX polish, billing, a full SaaS product
