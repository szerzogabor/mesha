# Infrastructure Architecture

## 1. Services Overview

| Component | Provider | Notes |
|---|---|---|
| Frontend | Vercel | Next.js, auto-deploy from GitHub |
| API Service | Render | Spring Boot, web service |
| Worker Service | Render | Spring Boot, worker dyno |
| PostgreSQL | Neon / Supabase / Render PG | Primary database |
| Redis | Upstash | Queue, cache, rate limiting |
| Object Storage | Cloudflare R2 | Attachments, logs, AI artifacts |

---

## 2. CI/CD

### Frontend
```
GitHub Push → Vercel Deploy (auto)
```

### Backend
```
GitHub Push → Render Deploy (auto)
```

---

## 3. Database

**Primary Database:** PostgreSQL

Purpose:
- Tickets, projects, workspaces
- AI sessions
- Workflow state
- PR metadata
- Comments
- Audit logs
- (Future) Embeddings via pgvector

Migrations managed with **Flyway**.

---

## 4. Redis

**Provider:** Upstash (serverless Redis)

Purpose:
- Job queueing (worker tasks)
- Session caching
- Rate limiting
- Temporary orchestration state

---

## 5. Object Storage

**Provider:** Cloudflare R2

Purpose:
- File attachments
- AI session logs
- Generated assets
- Build artifacts

---

## 6. Scaling Strategy

### Phase 1 — MVP
Single API service + single worker service on Render.

### Phase 2
Separate worker types:
- Orchestration workers
- Webhook processing workers
- Indexing workers

### Phase 3 — Scale
- Kubernetes
- Temporal.io for durable workflow execution
- Dedicated vector database
- Multi-agent routing

---

## 7. Observability

| Layer | Tool |
|---|---|
| Logging | Structured JSON (Logback) |
| Error Tracking | Sentry |
| Metrics | OpenTelemetry / Prometheus (future) |

---

## 8. Security Requirements

- Secrets in managed secret storage (never in code)
- Signed and verified webhook payloads
- Rate limiting on all public endpoints
- Audit logging for AI actions
- Repository permission isolation per workspace
