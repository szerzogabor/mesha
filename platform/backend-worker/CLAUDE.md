# Backend Worker — Agent Guide

Java 21 / Spring Boot 3.3 background worker service. Runs all `@Scheduled` jobs: session polling, Blocks session dispatch, AI workflow orchestration. **Does NOT expose any HTTP endpoints.** See root `CLAUDE.md` for git/PR rules.

---

## Critical Rules

- **All `@Scheduled` beans must be protected with `@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true")`.**
- **Flyway is disabled here** (`spring.flyway.enabled=false`) — database migrations are managed exclusively by `backend-api`.
- **No REST controllers** — this service has no HTTP layer (`spring.main.web-application-type=none`).
- **Never modify existing Flyway migrations** — add new `V{n+1}__*.sql` files in `backend-api` only.
- **No auto-merge logic** — AI creates PRs; humans merge.

---

## Running Locally

```bash
# Prerequisites: PostgreSQL + Redis running (platform/scripts/start-dev.sh)
# backend-api must have run Flyway migrations first
APP_WORKER_ENABLED=true mvn spring-boot:run   # worker process (no HTTP port)
mvn clean test                                 # run unit tests
mvn clean package                              # build fat JAR
docker build -t mesha-worker .                 # Docker image
```

---

## Package Map

```
src/main/java/com/mesha/api/
├── BackendWorkerApplication.java    # @SpringBootApplication + @EnableScheduling
├── ai/                              # AI draft generation (Anthropic/Blocks)
├── config/                          # Spring beans, Redis, encryption properties
├── dto/                             # DTOs shared with service layer
├── model/                           # JPA entities (duplicated from backend-api)
├── observability/                   # OTel helpers, MDC filters
├── repository/                      # Spring Data JpaRepository interfaces (duplicated)
├── service/                         # Business logic services (duplicated from backend-api)
└── worker/
    ├── scheduling/                  # SessionPollingScheduler (@ConditionalOnProperty)
    │                                # SessionPollService, SessionPollTransactions
    ├── blocks/                      # BlocksAdapter (HTTP client to Blocks API)
    ├── orchestration/               # ProviderAdapter interface, SessionRequest/Result
    └── observability/               # WorkflowTracer (structured logging)
```

> **Note:** `model/`, `repository/`, and `service/` are intentional duplicates of `backend-api`
> (no shared module). Keep them in sync manually when the domain model changes.

---

## Worker System

**Polling loop** (`worker/scheduling/`):
- `SessionPollingScheduler` runs `@Scheduled(fixedDelayString = "...")` every 5 seconds
- Protected by `@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true")`
- Finds all `BlocksSession` records not in terminal states (DONE/FAILED/CANCELED)
- Calls `SessionPollService.processSession(sessionId)` for each

**Poll cycle** (`SessionPollService`):
1. Acquire Redis distributed lock (key = sessionId, TTL = backoff interval)
2. Call `BlocksAdapter.pollSession()` → get latest state from Blocks API
3. Persist state update via `SessionPollTransactions`
4. On terminal state: fire `ActivityEvent`, trigger `AutomationService`
5. On error: increment retry count, apply exponential backoff

**Configuration switch:**
```yaml
app:
  worker:
    enabled: ${APP_WORKER_ENABLED:true}   # true in worker, false in backend-api
```

---

## Key Configuration (application.yml)

```yaml
spring:
  main:
    web-application-type: none   # no HTTP server
  flyway:
    enabled: false               # migrations managed by backend-api

app:
  worker:
    enabled: ${APP_WORKER_ENABLED:true}

mesha:
  polling:
    interval-ms: 5000
    backoff:
      base-ms: 5000
      max-ms: 300000
      multiplier: 2.0
    max-session-age-hours: 24
```

---

## Observability

- OpenTelemetry Java Agent attached at startup (see `Dockerfile`)
- `OTEL_SERVICE_NAME=mesha-worker` distinguishes traces from `mesha-api`
- Structured JSON logs via Logback + Logstash encoder
- Use `WorkflowTracer` for AI session log events

---

## Testing

Location: `src/test/java/com/mesha/api/`

Run: `mvn test`
