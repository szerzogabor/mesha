# Backend API — Agent Guide

Java 21 / Spring Boot 3.3 REST API service. Also contains the **active worker** (session polling, Blocks orchestration). See root `CLAUDE.md` for git/PR rules.

---

## Critical Rules

- **All worker changes go in `src/main/java/com/mesha/api/worker/`** within this module.
- **Never modify existing Flyway migrations** — add a new `V{n+1}__*.sql` file.
- **No auto-merge logic** — AI creates PRs; humans merge.

---

## Running Locally

```bash
# Prerequisites: PostgreSQL + Redis running (platform/scripts/start-dev.sh)
mvn spring-boot:run          # API at http://localhost:8080
mvn clean test               # Run unit tests
mvn clean package            # Build fat JAR
docker build -t mesha-api .  # Docker image (multi-stage)
```

Health check: `GET /actuator/health`

---

## Package Map

```
src/main/java/com/mesha/api/
├── BackendApiApplication.java       # @SpringBootApplication + @EnableScheduling
├── controller/                      # REST endpoints (one controller per domain)
├── service/                         # Business logic (one service per domain)
├── model/                           # JPA entities
├── repository/                      # Spring Data JpaRepository interfaces
├── dto/                             # Request/Response DTOs
│   └── Naming: CreateXRequest, UpdateXRequest, XDto
├── ai/                              # AI draft generation (Anthropic/Blocks)
├── worker/
│   ├── scheduling/                  # SessionPollingScheduler, SessionPollService
│   ├── blocks/                      # BlocksAdapter (HTTP client to Blocks API)
│   ├── orchestration/               # ProviderAdapter interface, SessionRequest/Result
│   └── observability/               # WorkflowTracer (structured logging)
├── security/                        # Clerk JWT verification (JWKS)
├── config/                          # Spring beans, CORS, Redis
└── observability/                   # OTel helpers, correlation ID filter
```

---

## Domain Entities (JPA Models)

| Entity | Table | Key Fields |
|--------|-------|-----------|
| `Workspace` | `workspaces` | id, name, slug |
| `User` | `users` | id, clerkId, email |
| `WorkspaceMember` | `workspace_members` | workspaceId, userId, role |
| `Project` | `projects` | id, workspaceId, name, key |
| `Issue` | `issues` | id, projectId, title, status, priority, assigneeId |
| `IssueStatus` | `project_statuses` | id, projectId, name, color, position |
| `Label` | `labels` | id, workspaceId, name, color |
| `Comment` | `comments` | id, issueId, authorId, content |
| `ActivityEvent` | `activity_events` | id, issueId, type, oldValue, newValue |
| `BlocksSession` | `blocks_sessions` | id, issueId, provider, providerSessionId, executionState |
| `BlocksMessage` | `blocks_messages` | id, sessionId, role, content, offset |
| `BlocksWebhookEvent` | `blocks_webhook_events` | id, payload, processed |
| `GitHubInstallation` | `github_installations` | id, workspaceId, installationId |
| `GitHubRepository` | `github_repositories` | id, installationId, fullName |
| `GitHubPullRequest` | `github_pull_requests` | id, sessionId, prNumber, state, url |
| `GitHubWebhookEvent` | `github_webhook_events` | id, deliveryId, event, payload |
| `AIDraft` | `ai_drafts` | id, workspaceId, prompt, status, generatedContent |
| `AutomationRule` | `automation_rules` | id, projectId, triggerType, triggerValue |
| `TicketRule` | `ticket_rules` | id, projectId, conditions, restrictions |
| `IssueLink` | `issue_links` | id, sourceIssueId, targetIssueId, type |

---

## Worker System

**Polling loop** (`worker/scheduling/`):
- `SessionPollingScheduler` runs `@Scheduled(fixedDelayString = "...")` every 5 seconds
- Finds all `BlocksSession` records not in terminal states (DONE/FAILED/CANCELED)
- Calls `SessionPollService.poll(session)` for each

**Poll cycle** (`SessionPollService`):
1. Acquire Redis distributed lock (key = sessionId, TTL = backoff interval)
2. Call `BlocksAdapter.pollSession()` → get latest state from Blocks API
3. Persist state update via `SessionPollTransactions`
4. On terminal state: fire `ActivityEvent`, trigger `AutomationService`
5. On error: increment retry count, apply exponential backoff

**Backoff config** (`application.yml`):
```yaml
mesha:
  polling:
    interval-ms: 5000
    backoff:
      base-ms: 5000
      max-ms: 300000
      multiplier: 2.0
    max-session-age-hours: 24
```

**ProviderAdapter interface:**
```java
String providerName();
SessionResult createSession(SessionRequest request);
SessionResult pollSession(String providerSessionId);
void cancelSession(String providerSessionId); // default no-op
```

`BlocksAdapter` is the current implementation. Add new providers by implementing this interface and wiring in `config/`.

---

## Adding a New Feature — Checklist

1. **Migration** — add `V{n+1}__your_feature.sql` in `src/main/resources/db/migration/`
2. **Entity** — add `@Entity` class in `model/`
3. **Repository** — add `JpaRepository` interface in `repository/`
4. **Service** — add business logic in `service/`; inject repository
5. **DTO** — add request/response classes in `dto/`
6. **Controller** — add `@RestController` in `controller/`; inject service
7. **Test** — add test in `src/test/java/com/mesha/api/`

**Conventions:**
- Entity IDs: `UUID`, generated with `@GeneratedValue(strategy = GenerationType.UUID)`
- Timestamps: `@CreatedDate` / `@LastModifiedDate` (Spring Data auditing)
- Soft deletes: check existing patterns in the codebase before implementing
- Pagination: return `PagedResponse<YourDto>` from list endpoints

---

## Security

- All endpoints secured via `Spring Security` + Clerk JWT
- Clerk JWKS URI configured in `application.yml` (`clerk.jwks-uri`)
- Webhooks (`/api/webhooks/*`) are excluded from JWT auth but have signature validation
- Extract current user: inject `@AuthenticationPrincipal` or use `SecurityContext`

---

## Testing

Location: `src/test/java/com/mesha/api/`

Existing tests:
- `SessionPollTransactionsTokenLimitTest` — polling logic edge cases
- `BlocksAdapterTest` — Blocks API HTTP client
- `AutomationServiceTest` — automation rule evaluation

Run: `mvn test`

---

## Observability

- OpenTelemetry Java Agent attached at startup (see `Dockerfile`)
- Structured JSON logs via Logback + Logstash encoder
- Loki4j appender active in local dev profile
- Use `WorkflowTracer` for AI session log events (not plain `log.info`)
- Correlation IDs: added by filter in `observability/`, propagated to logs and traces
