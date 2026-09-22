# Design Documentation (Phase 0)

Design for the **Account Takeover (ATO) Containment and Recovery Service for Small SaaS Applications**.
Diagrams use [Mermaid](https://mermaid.js.org/); they render on GitHub and in the VS Code Markdown preview
(with the "Markdown Preview Mermaid Support" extension).

| Document | Covers |
|---|---|
| [01-actors-and-scope.md](01-actors-and-scope.md) | Actors, roles, permissions, design decisions, scope |
| [02-architecture-and-modules.md](02-architecture-and-modules.md) | System architecture, module list, token design, AWS mapping |
| [03-ato-workflow.md](03-ato-workflow.md) | ATO lifecycle, risk scoring, containment, recovery, state machines |
| [04-database-design.md](04-database-design.md) | ER diagram and table descriptions (SQL in `database/schema_draft.sql`) |
| [05-api-design.md](05-api-design.md) | REST conventions and the full API list |
| [06-threat-scenarios.md](06-threat-scenarios.md) | Threat scenarios, expected detection/response, test plan |

## Key decisions at a glance

- **One Spring Boot application**, organised into modules (packages). No microservices.
- **Multi-tenant**: one database, every row carries a `tenant_id`. The tenant always comes from the
  logged-in user's token, never from the request.
- **The project includes a small demo SaaS** (users, login, sessions) so the ATO features have something to protect.
  Later it could be exposed as an integration API for other SaaS apps.
- **Email and geo-IP are simulated locally** (emails are written to a `notifications` table; country and device
  come from request headers in the dev profile). Real services come with AWS.
- **Tokens**: short-lived JWT access token tied to a server-side session, plus a rotating refresh token.
  Revoking the session kills access immediately. This is what makes containment work.

## Suggested phase roadmap

This is a suggestion. Adjust it to your mentor's plan.

| Phase | Deliverable |
|---|---|
| 0 | Design documents (this folder) |
| 1 | Local environment: backend, frontend, PostgreSQL, health check |
| 2 | Tenants, users, authentication (register/login, BCrypt, JWT) |
| 3 | Session management and refresh tokens |
| 4 | Risk scoring / ATO detection on login |
| 5 | Token replay detection |
| 6 | Incident management and audit logging |
| 7 | Account containment |
| 8 | Account recovery and step-up verification |
| 9 | Tenant isolation hardening and cross-tenant tests |
| 10 | Frontend dashboards and attacker simulator |
| 11 | AWS deployment with Terraform |
