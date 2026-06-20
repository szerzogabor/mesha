# Mesha Codebase Reference

Ground-truth map of what is actually implemented. Keep this file updated when adding new features.

---

## Domain Model

```
Workspace
  ├── WorkspaceMembers (users + roles: OWNER, ADMIN, DEVELOPER, VIEWER)
  ├── WorkspaceBlocksConfig (encrypted Blocks API key)
  ├── GitHubInstallations (GitHub App OAuth per workspace)
  └── Projects
        ├── ProjectStatuses (custom workflow statuses)
        ├── Labels (workspace-scoped tags)
        ├── AutomationRules (trigger → action workflows)
        ├── TicketRules (conditional restrictions)
        └── Issues
              ├── Assignee + Labels (M2M)
              ├── Comments (threaded)
              ├── ActivityEvents (full changelog)
              ├── IssueLinks (relationships between issues)
              ├── BlocksSessions (AI execution lifecycle)
              │     ├── BlocksMessages (chat history)
              │     └── GitHubPullRequest (outcome)
              └── ConnectorAgentSessions (self-hosted connector execution lifecycle)
                    └── ConnectorAgentSessionMessages (follow-up chat, claim-and-deliver)

ConnectorAgent (registered self-hosted executor, polls for queued ConnectorAgentSessions)
```

---

## Key Enums

**AIExecutionState** (BlocksSession lifecycle):
```
CREATED → PLANNING → EXECUTING → WAITING_REVIEW → PR_OPENED → DONE
                                                  ↓
                                               FAILED / CANCELED
```

**ConnectorAgentSessionStatus** (ConnectorAgentSession lifecycle — self-hosted connector polling):
```
CREATED → QUEUED → CLAIMED → PREPARING → RUNNING ⇄ WAITING_FOR_USER → COMPLETED
                                                                     ↓
                                                              FAILED / CANCELLED
```

**IssuePriority:** `LOW`, `MEDIUM`, `HIGH`, `URGENT`

**AutomationTriggerType:** `PR_CREATED`, `PR_MERGED`, `BLOCKS_SESSION_COMPLETED`, and others

**TicketRuleConditionType:** `HAS_STATUS`, `HAS_LABEL`

**TicketRuleRestrictionType:** `CANNOT_START_AI_SESSION`, `CANNOT_MOVE_TO_STATUS`

**AIDraftStatus:** `PENDING`, `COMPLETED`, `FAILED`, `APPROVED`, `REJECTED`

---

## Database Migrations (Flyway)

Location: `platform/backend-api/src/main/resources/db/migration/`

**Never modify existing migration files.** Always add `V{n+1}__description.sql`.

Current highest: **V32** (`V32__deduplicate_and_unique_github_prs.sql`)

| Version | Purpose |
|---------|---------|
| V1 | Initial schema (workspaces, projects, issues, comments) |
| V2 | Auth — users, workspace members, roles |
| V3 | Labels, assignees, activity events, indexes |
| V4 | GitHub installations, repositories, audit logs |
| V5 | AI draft generation table |
| V7 | BlocksSessions (core AI execution tracking) |
| V9 | Blocks webhook event log |
| V10 | Workspace Blocks config (encrypted API keys) |
| V11 | BlocksMessages (session chat history) |
| V16 | Multi-session support per issue |
| V18 | Human-readable issue IDs |
| V21 | Custom project statuses |
| V22–V23 | Automation rules + actions |
| V24 | Issue links |
| V25 | Agent assignee type on issues |
| V28 | Ticket rules (conditions + restrictions) |
| V32 | Deduplicate GitHub PRs |
| V43–V44 | ConnectorAgentSessions (self-hosted connector execution tracking, workspace path) |
| V45 | ConnectorAgentSessionMessages (follow-up chat, claim-and-deliver) |
| V46 | Pull request fields on ConnectorAgentSessions (connector-reported, not webhook-synced) |

---

## REST API Endpoints

Base: `http://localhost:8080` (dev) / Render URL (prod)

All endpoints require `Authorization: Bearer <clerk-jwt>` **except** `/api/webhooks/*`.

| Controller | Key Endpoints |
|-----------|--------------|
| IssueController | `GET/POST /api/projects/{projectId}/issues` |
| | `GET/PATCH/DELETE /api/projects/{projectId}/issues/{issueId}` |
| | `GET /api/projects/{projectId}/issues/{issueId}/activity` |
| | `GET /api/projects/{projectId}/issues/stream` (SSE) |
| AIDraftController | `POST /api/ai/drafts` — generate draft |
| | `POST /api/ai/drafts/{id}/approve` — create issue from draft |
| | `POST /api/ai/drafts/{id}/regenerate` |
| BlocksSessionController | `GET /api/issues/{issueId}/sessions` |
| | `POST /api/issues/{issueId}/assign-blocks` — start AI session |
| | `PATCH /api/sessions/{sessionId}` |
| BlocksWebhookController | `POST /api/webhooks/blocks` (no auth) |
| GitHubAppController | `GET /api/github/installations` |
| GitHubWebhookController | `POST /api/webhooks/github` (no auth) |
| GitHubPullRequestController | `GET /api/pull-requests`, `POST /api/pull-requests/sync` |
| CommentController | `GET/POST /api/issues/{issueId}/comments` |
| LabelController | `GET/POST /api/workspaces/{workspaceId}/labels` |
| ProjectController | `GET/POST /api/workspaces/{workspaceId}/projects` |
| ProjectStatusController | `GET/POST /api/projects/{projectId}/statuses` |
| WorkspaceController | `GET/POST /api/workspaces` |
| AutomationRuleController | `GET/POST /api/projects/{projectId}/automation-rules` |
| TicketRuleController | `GET/POST /api/projects/{projectId}/ticket-rules` |
| IssueLinkController | `GET/POST /api/issues/{issueId}/links` |
| AuthController | `POST /api/auth/sync` (sync Clerk user) |
| AgentSessionController | `GET/POST /api/agent-sessions` — web app: create/list connector agent sessions |
| | `POST /api/agent-sessions/{sessionId}/enqueue`, `/cancel` |
| | `GET/POST /api/agent-sessions/{sessionId}/messages` — follow-up chat |
| ConnectorAgentSessionController | `POST /api/connector/agent-sessions/claim` — connector: claim next queued session (connector token auth) |
| | `POST /api/connector/agent-sessions/{sessionId}/status`, `GET .../context` |
| | `GET /api/connector/agent-sessions/{sessionId}/messages` — claim-and-deliver pending follow-ups |
| | `POST /api/connector/agent-sessions/{sessionId}/pull-request` — report opened PR |

**Pagination:** `PagedResponse<T>` — `{ content, page, size, totalElements, totalPages, last }`

---

## Worker System

Worker logic is embedded in `backend-api`.

**Path:** `platform/backend-api/src/main/java/com/mesha/api/worker/`

### Components

| Component | Responsibility |
|-----------|---------------|
| `SessionPollingScheduler` | `@Scheduled` every 5s — finds active sessions and triggers polling |
| `SessionPollService` | Calls Blocks API, updates session state, handles backoff |
| `SessionPollTransactions` | DB transaction management for polling |
| `BlocksAdapter` | HTTP client to Blocks REST API (implements `ProviderAdapter`) |
| `ProviderAdapter` | Interface — pluggable AI provider (Blocks today, others tomorrow) |
| `BlocksApiKeyService` | Fetches per-workspace Blocks API key from config |
| `WorkflowTracer` | Structured logging for AI execution events |

### Polling Config (application.yml)
- Interval: 5s
- Backoff base: 5s, max: 300s, multiplier: 2.0
- Max session age: 24 hours
- Redis distributed lock prevents parallel poll of same session

---

## Background Systems

### Automation Rules Engine
- Evaluates `AutomationRule` triggers synchronously in service layer on state changes
- Trigger events: PR created, PR merged, Blocks session completed, etc.
- Actions: move issue to status, assign label, etc.

### Ticket Rules Enforcement
- Pre-action validation before AI session start or status transition
- Conditions use AND logic: `HAS_STATUS + HAS_LABEL`
- Restrictions: `CANNOT_START_AI_SESSION`, `CANNOT_MOVE_TO_STATUS`

### Activity Tracking
- `ActivityService` records all state changes to `ActivityEvent`
- Shown as changelog in issue detail view

---

## Backend Package Structure

```
com.mesha.api/
├── BackendApiApplication.java     # Entry point (@EnableScheduling)
├── controller/                    # REST + WebSocket endpoints
├── service/                       # Business logic
├── model/                         # JPA entities
├── repository/                    # Spring Data repos
├── dto/                           # Request/response DTOs (~40 classes)
├── ai/                            # AI orchestration (Blocks, Claude)
├── worker/                        # Active worker logic
│   ├── scheduling/                # SessionPollingScheduler, SessionPollService
│   ├── blocks/                    # BlocksAdapter, BlocksApiKeyService
│   ├── orchestration/             # ProviderAdapter interface, SessionRequest/Result
│   └── observability/             # WorkflowTracer
├── security/                      # JWT auth (Clerk JWKS)
├── config/                        # Spring configuration beans
└── observability/                 # OTel utilities, correlation IDs
```

---

## Frontend Structure

```
platform/frontend/src/
├── app/                           # Next.js app router (file-based routing)
│   └── workspaces/[workspaceId]/
│       ├── projects/[projectId]/
│       │   └── issues/[issueId]/  # Issue detail page
│       ├── blocks/                # Blocks config UI
│       └── github/                # GitHub integration UI
├── components/                    # React component library
│   ├── automation/, blocks/, comments/, github/
│   ├── issues/, projects/, settings/, ui/
│   └── activity/, layout/
├── hooks/                         # Custom React hooks (85+ files)
│   ├── useIssues.ts, useBlocksSessions.ts
│   ├── useAIDraft.ts, useWorkspaces.ts, etc.
├── types/index.ts                 # Shared TypeScript interfaces
└── lib/
    ├── api-client.ts              # REST API wrapper (auth + correlation IDs)
    ├── otel/                      # OpenTelemetry config
    └── logger.ts                  # Structured logging
```

**State management:**
- Server state: TanStack Query (React Query 5) via custom hooks in `hooks/`
- Client state: Zustand stores (initialized in `app/providers.tsx`)
- Auth: Clerk (tokens injected by `api-client.ts`)

---

## CI/CD

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | Push/PR | Path-filtered: lint, type-check, build, test per changed module |
| `e2e-frontend.yml` | Push/PR | Playwright end-to-end tests |
| `e2e-backend.yml` | Push/PR | Backend integration tests |
| `linear-status-update.yml` | PR events | Sync Linear ticket status |
| `branch-protection.yml` | Validation | Enforce branch rules on main |

**Deployment:**
- Frontend → Vercel (auto-deploy on main)
- Backend → Render (Docker container, `render.yaml` IaC)
- DB → Render PostgreSQL, Cache → Upstash Redis

---

## Observability Stack

- **Traces:** OpenTelemetry Java Agent → Grafana Tempo
- **Logs:** Logback (JSON/Logstash encoder) + Loki4j → Grafana Loki
- **Metrics:** Spring Boot Actuator → Prometheus → Grafana
- **Correlation:** W3C `traceparent` headers + custom `X-Correlation-ID`
- **Frontend traces:** OpenTelemetry SDK (initialized in `instrumentation-client.ts`)
- **Health check:** `GET /actuator/health`

---

## Environment Variables

**Backend** (`application.yml` env substitution):

| Variable | Purpose |
|----------|---------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `REDIS_URL` | Upstash Redis |
| `CLERK_JWKS_URI` | JWT verification |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` | Claude AI |
| `BLOCKS_API_URL`, `BLOCKS_API_KEY` | Blocks AI provider |
| `BLOCKS_ENCRYPTION_SECRET`, `BLOCKS_WEBHOOK_SECRET` | Blocks security |
| `GITHUB_APP_ID`, `GITHUB_APP_CLIENT_ID`, `GITHUB_APP_CLIENT_SECRET` | GitHub App |
| `GITHUB_APP_PRIVATE_KEY`, `GITHUB_APP_WEBHOOK_SECRET` | GitHub App |
| `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORTER_OTLP_HEADERS` | Grafana Cloud |

**Frontend** (`.env.local`):

| Variable | Purpose |
|----------|---------|
| `NEXT_PUBLIC_API_URL` | Backend API base URL |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Clerk public key |
| `CLERK_SECRET_KEY` | Clerk server-side key |
| `NEXT_PUBLIC_OTEL_EXPORTER_OTLP_ENDPOINT` | Grafana Cloud gateway |
| `NEXT_PUBLIC_OTEL_EXPORTER_OTLP_AUTH` | Base64 encoded credentials |

---

## Key File Paths

| What | Where |
|------|-------|
| Spring Boot entry point | `platform/backend-api/src/main/java/com/mesha/api/BackendApiApplication.java` |
| Spring Boot config | `platform/backend-api/src/main/resources/application.yml` |
| DB migrations | `platform/backend-api/src/main/resources/db/migration/V*.sql` |
| Worker scheduler | `platform/backend-api/src/main/java/com/mesha/api/worker/scheduling/SessionPollingScheduler.java` |
| Blocks adapter | `platform/backend-api/src/main/java/com/mesha/api/worker/blocks/BlocksAdapter.java` |
| Frontend root layout | `platform/frontend/src/app/layout.tsx` |
| Frontend providers | `platform/frontend/src/app/providers.tsx` |
| API client | `platform/frontend/src/lib/api-client.ts` |
| TypeScript types | `platform/frontend/src/types/index.ts` |
| Dev infrastructure start | `platform/scripts/start-dev.sh` |
| Render IaC | `render.yaml` |
| Docker Compose (dev) | `platform/infrastructure/docker-compose.dev.yml` |
