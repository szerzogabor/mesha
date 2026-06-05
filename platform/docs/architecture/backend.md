# Backend Architecture

## 1. Services

### API Service (`backend-api`)

**Platform:** Render
**Technology:** Java 21, Spring Boot 3, Spring Security

Responsibilities:
- REST APIs
- WebSocket notifications
- Authentication & authorization
- CRUD operations (tickets, PRs, comments)

### AI Worker Service (`backend-worker`)

**Platform:** Render (worker dyno)
**Technology:** Java 21, Spring Boot 3, scheduled jobs / consumers

Responsibilities:
- Blocks session orchestration
- Session polling and retry handling
- GitHub webhook processing
- Embedding generation
- Context loading
- Workflow execution

---

## 2. Recommended Module Structure

```
backend-api/
 ├── auth-module
 ├── issue-module
 ├── comment-module
 ├── ai-module
 ├── github-module
 └── websocket-module

backend-worker/
 ├── orchestration-module
 ├── blocks-module
 ├── github-webhook-module
 ├── indexing-module
 └── retry-module
```

---

## 3. Domain Model

```
Workspace
 └── Project
      └── Issue
           ├── Comment
           ├── AIThread
           ├── PullRequest
           ├── Attachment
           └── WorkflowRun
```

### Entity Descriptions

**Workspace** — Represents an organization or team.

**Project** — Container for issues and repositories.

**Issue** — Core project management task.
Fields: title, description, status, labels, assignee, priority, AI assignment status.

**AIThread** — Represents a persistent AI conversation.
Fields: provider, providerSessionId, state, contextVersion.

**WorkflowRun** — Tracks long-running orchestration flows (ticket generation, code implementation, PR review).

---

## 4. Database Schema

### issues
```sql
CREATE TABLE issues (
    id          UUID PRIMARY KEY,
    project_id  UUID NOT NULL,
    title       TEXT NOT NULL,
    description TEXT,
    status      VARCHAR(30),
    priority    VARCHAR(20),
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);
```

### ai_sessions
```sql
CREATE TABLE ai_sessions (
    id                  UUID PRIMARY KEY,
    issue_id            UUID NOT NULL,
    provider            VARCHAR(50) NOT NULL,
    provider_session_id TEXT NOT NULL,
    status              VARCHAR(30) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);
```

### pull_requests
```sql
CREATE TABLE pull_requests (
    id             UUID PRIMARY KEY,
    issue_id       UUID NOT NULL,
    github_pr_number INTEGER,
    github_repo    TEXT,
    title          TEXT,
    state          VARCHAR(20),
    url            TEXT,
    created_at     TIMESTAMP NOT NULL
);
```

---

## 5. AI Ticket Generation Flow

```
Frontend
  ↓
POST /api/ai/ticket-drafts
  ↓
AI Orchestrator → Prompt Builder → Blocks/OpenAI
  ↓
Draft Response → Persist Draft → Return to UI
```

Draft ticket contains: title, technical summary, acceptance criteria, scope, out-of-scope, suggested labels, estimated complexity, suggested repository.

---

## 6. AI Assignment Flow

```
Issue Approved
    ↓
Workflow Event Published
    ↓
AI Worker Consumes Event
    ↓
Context Loader → Blocks Session Created
    ↓
Repository Analysis → Implementation → GitHub PR Created
    ↓
Webhook Received → PR Synced To Platform
```

---

## 7. Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Security | Spring Security |
| Database | PostgreSQL + Flyway |
| Cache / Queue | Redis (Upstash) |
| Object Storage | Cloudflare R2 |
| Vector Search | pgvector (future) |
| Observability | Structured JSON logs, OpenTelemetry, Grafana Cloud (Loki + Tempo + Prometheus) |
