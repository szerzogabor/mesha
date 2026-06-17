# Architecture Documentation

This directory contains architectural documentation for every major subsystem in Mesha.

## Index

| Document | Subsystem |
|----------|-----------|
| [system-overview.md](system-overview.md) | High-level architecture, module relationships, data flow, external integrations |
| [authentication.md](authentication.md) | Clerk JWT authentication, RBAC, webhook signature validation |
| [project-management.md](project-management.md) | Workspaces, projects, issues, statuses, labels, issue links |
| [ai-execution.md](ai-execution.md) | AI session lifecycle, ProviderAdapter abstraction, Blocks integration, polling worker |
| [ai-draft.md](ai-draft.md) | AI-powered ticket draft generation via Anthropic Claude |
| [github-integration.md](github-integration.md) | GitHub App OAuth, repository sync, pull request tracking, webhooks |
| [automation.md](automation.md) | Automation rules (trigger → action) and ticket rules (conditional restrictions) |
| [observability.md](observability.md) | OpenTelemetry tracing, structured logging, Grafana Cloud, correlation IDs |

---

## Reading Order for New Contributors

1. [system-overview.md](system-overview.md) — understand the big picture first
2. [authentication.md](authentication.md) — understand how auth works before touching any endpoint
3. [project-management.md](project-management.md) — the core domain model
4. [ai-execution.md](ai-execution.md) — the most complex subsystem; read before touching worker or session code
5. [github-integration.md](github-integration.md) — read before touching webhooks or PR sync
6. [automation.md](automation.md) — read before touching state-change logic

---

## Key Invariants

The following must never be violated:

1. `backend-api` runs **no** `@Scheduled` jobs — all scheduling is in `backend-worker`.
2. Flyway migration files are **immutable** — never modify existing `.sql` files.
3. AI never merges or deploys — humans must approve all merges.
4. All API endpoints require Clerk JWT except `/api/webhooks/*`.
5. Webhook endpoints validate HMAC signatures.
6. `ProviderAdapter` is the only interface through which AI providers are called.
7. Frontend never reads from the database directly.
