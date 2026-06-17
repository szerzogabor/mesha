# System Overview

## High-Level Architecture

Mesha is an AI-native project management platform. Users create tickets from natural language, assign them to AI agents (via Blocks), and review the resulting GitHub Pull Requests. The system enforces human-in-the-loop — AI never merges or deploys without explicit approval.

```
┌─────────────────────────────────────────────────────────────────┐
│                          Vercel (Frontend)                       │
│               Next.js 15 · TypeScript · TailwindCSS             │
│         TanStack Query · Zustand · Clerk · OpenTelemetry        │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTPS REST + SSE
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Render (backend-api)                          │
│        Spring Boot 3 · Java 21 · REST API (port 10000)         │
│     Clerk JWT auth · Spring Security · OpenTelemetry Agent      │
└──────────────────┬──────────────────────┬───────────────────────┘
                   │ JPA / JDBC           │ Redis (locks/cache)
                   ▼                      ▼
┌──────────────────────────┐   ┌──────────────────────────────────┐
│  PostgreSQL 16 (Render)  │   │    Redis / Upstash (serverless)  │
│  Flyway migrations       │   │    Distributed locks             │
└──────────────────────────┘   └──────────────────────────────────┘
                   ▲
                   │ JPA / JDBC
┌──────────────────┴───────────────────────────────────────────────┐
│                    Render (backend-worker)                        │
│        Spring Boot 3 · Java 21 · No HTTP port                   │
│        APP_WORKER_ENABLED=true · SessionPollingScheduler (5s)    │
└──────────────────────────────┬───────────────────────────────────┘
                               │ REST (Blocks API)
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     External: Blocks API                         │
│           AI agent orchestration (code writing, PRs)            │
└─────────────────────────────────────────────────────────────────┘
                               │ GitHub App JWT + Webhooks
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     External: GitHub                             │
│              GitHub App · Repositories · Pull Requests          │
└─────────────────────────────────────────────────────────────────┘
                               │ Traces + Logs + Metrics
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Grafana Cloud (Observability)                     │
│           Tempo (traces) · Loki (logs) · Prometheus (metrics)   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Module Relationships

```
Workspace
├── WorkspaceMembers  (users + roles: OWNER/ADMIN/DEVELOPER/VIEWER)
├── WorkspaceBlocksConfig  (encrypted Blocks API key)
├── GitHubInstallations  (per-workspace GitHub App OAuth)
├── Labels  (workspace-scoped)
├── AgentDefinitions  (custom AI agent configs)
└── Projects
      ├── ProjectStatuses  (custom ordered statuses)
      ├── AutomationRules  (trigger → actions)
      ├── TicketRules  (conditional restrictions)
      └── Issues
            ├── Comments
            ├── ActivityEvents  (immutable changelog)
            ├── IssueLabels  (M2M)
            ├── IssueLinks  (BLOCKS/RELATES_TO/DUPLICATES)
            ├── IssueAgents  (agent assignments)
            └── BlocksSessions  (AI execution)
                  ├── BlocksMessages  (chat history)
                  └── GitHubPullRequests  (outcome)
```

---

## Data Flow

### Issue Creation Flow
```
User → Frontend → POST /api/projects/{id}/issues
     → IssueService.createIssue()
     → ActivityService.log(ISSUE_CREATED)
     → AutomationService.evaluate(ISSUE_CREATED trigger)
     → Response: Issue DTO
```

### AI Execution Flow
```
User → Frontend → POST /api/issues/{id}/assign-blocks
     → BlocksSessionService.startSession()
     → BlocksAdapter.startSession()  [backend-api creates session]
     → BlocksSession persisted (state: CREATED)

[Worker polls every 5s]
SessionPollingScheduler → SessionPollService → BlocksAdapter.pollSession()
     → Update BlocksSession state (PLANNING → EXECUTING → WAITING_REVIEW → PR_OPENED → DONE)
     → ActivityService.log(AI_STATE_CHANGED)
     → On terminal state: AutomationService.evaluate(BLOCKS_SESSION_COMPLETED trigger)

[GitHub PR creation]
     → GitHubAppService.createPullRequest()
     → GitHubPullRequestService.persist(pr)
     → ActivityService.log(AI_PR_OPENED)
```

### GitHub Webhook Flow
```
GitHub → POST /api/webhooks/github  [no auth, HMAC validated]
     → GitHubWebhookService.process(event)
     → GitHubPullRequestService.sync(pr state)
     → AutomationService.evaluate(PR_MERGED trigger)  [if applicable]
```

### AI Draft Flow
```
User → POST /api/ai/drafts  {prompt}
     → AIDraftService → ClaudeAIAdapter → Anthropic API
     → AIDraft persisted (status: COMPLETED)
     → User reviews → POST /api/ai/drafts/{id}/approve
     → IssueService.createIssue(from draft)
     → ActivityService.log(ISSUE_CREATED_FROM_AI_DRAFT)
```

---

## External Integrations

| Service | Purpose | Auth Method |
|---------|---------|-------------|
| **Clerk** | User authentication & identity | JWKS (RS256 JWT validation) |
| **Blocks API** | AI agent orchestration | Workspace-scoped API key (AES-256 encrypted at rest) |
| **Anthropic Claude** | AI draft generation | API key (env var) |
| **GitHub** | Repository management, PR creation | GitHub App JWT (RS256) + Installation token |
| **Vercel** | Frontend hosting & preview deployments | Deployment hooks |
| **Render** | Backend API + Worker hosting | Git-based auto-deploy |
| **PostgreSQL (Render)** | Primary database | Connection string |
| **Redis (Upstash)** | Distributed locks & caching | Connection URL |
| **Grafana Cloud** | Traces, logs, metrics | OTLP + auth token |

---

## Critical Workflows

### Session State Machine
```
CREATED → PLANNING → EXECUTING → WAITING_REVIEW → PR_OPENED → DONE
                                                  ↓
                                           FAILED / CANCELED
```
- State transitions are persisted in `blocks_sessions.execution_state`
- Each transition fires an `ActivityEvent` (type: `AI_STATE_CHANGED`)
- Terminal states (`DONE`, `FAILED`, `CANCELED`) trigger `AutomationService`

### Automation Rule Evaluation
- Evaluated **synchronously** from the service layer on state changes
- Triggers: `PR_CREATED`, `PR_MERGED`, `BLOCKS_SESSION_COMPLETED`, `ISSUE_CREATED`, `STATUS_CHANGED`
- Actions: `MOVE_TO_STATUS`, `ADD_LABEL`, `REMOVE_LABEL`, `ASSIGN_USER`

### Distributed Polling Lock
- Worker acquires a Redis lock per `BlocksSession` before polling
- Prevents concurrent polls of the same session across restarts
- Lock TTL = backoff interval (5s base, up to 300s max)

---

## Deployment Topology

| Service | Host | HTTP | Scheduled Jobs |
|---------|------|------|----------------|
| `frontend` | Vercel | Yes (port 3000) | No |
| `backend-api` | Render | Yes (port 10000) | **No** |
| `backend-worker` | Render | **No** | **Yes** (every 5s) |
| `postgresql` | Render managed | No | N/A |
| `redis` | Upstash serverless | No | N/A |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 15, React 19, TypeScript, TailwindCSS |
| Frontend state | TanStack Query 5 (server), Zustand (client) |
| Frontend auth | Clerk |
| Backend | Spring Boot 3.3, Java 21 |
| Backend auth | Spring Security + Clerk JWKS |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 + Flyway |
| Cache / Locks | Redis (Upstash) |
| AI Orchestration | Blocks (via ProviderAdapter) |
| AI Drafts | Anthropic Claude |
| GitHub | GitHub App (JWT) + Webhooks |
| Observability | OpenTelemetry → Grafana Cloud (Tempo, Loki, Prometheus) |
| CI/CD | GitHub Actions + Vercel preview + Render auto-deploy |
