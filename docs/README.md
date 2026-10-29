# Design Documentation

Design and implementation notes for the **Account Takeover (ATO) Containment and Recovery Service for Small SaaS Applications**. The API reference and database status section distinguish current code from planned design.
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
- **Recovery delivery**: the current local verification provider does not send email or SMS. Test codes are returned only when `APP_RECOVERY_EXPOSE_TEST_CODE=true` is explicitly set.
- **Sessions**: bearer tokens are opaque random values; the database stores their SHA-256 hashes. The current implementation does not use JWT or refresh-token rotation.
- **Location and device signals**: the risk engine uses request-provided device and simulated-location values; it does not perform geo-IP lookup.

## Suggested roadmap

This roadmap describes remaining design work, not a claim that these items are all unfinished. Implemented behavior is summarized in the README and API reference.

| Phase | Deliverable |
|---|---|
| Area | Remaining work |
|---|---|
| Authentication | Add a real step-up challenge and consider refresh-token rotation |
| Recovery | Add a real verification delivery provider and complete integration checks |
| Frontend | Replace dashboard and account-history placeholders; wire manual containment only if required |
| Operations | Add database migrations and production deployment configuration |
| AWS | No infrastructure or EBS snapshot recovery workflow is specified in the current application requirements; define the AWS scope before adding either |
