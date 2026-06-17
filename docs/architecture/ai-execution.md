# AI Execution

## Purpose

Orchestrate AI agents to implement software features autonomously. When a user assigns an issue to an AI agent, the system creates a `BlocksSession`, delegates work to the Blocks API, polls for progress, and tracks the resulting GitHub Pull Request — all with human oversight.

---

## Responsibilities

- Start AI sessions via `ProviderAdapter` abstraction
- Poll session state from Blocks API every 5 seconds (in `backend-worker`)
- Transition `BlocksSession` through the state machine
- Persist chat messages (`BlocksMessages`) for auditability
- Track resulting GitHub PRs (`GitHubPullRequest`)
- Fire `ActivityEvent` on every state transition
- Trigger `AutomationService` on terminal states
- Apply exponential backoff on polling errors
- Prevent concurrent polling via distributed Redis locks

---

## Public Interfaces

### REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/issues/{id}/sessions` | List sessions for an issue |
| `POST` | `/api/issues/{id}/assign-blocks` | Start a new AI session |
| `PATCH` | `/api/sessions/{id}` | Update session (e.g., cancel) |

### Webhook Endpoint
```
POST /api/webhooks/blocks
```
Receives push events from Blocks API (no auth, HMAC-SHA256 validated).

### ProviderAdapter Interface
```java
public interface ProviderAdapter {
    SessionResult startSession(SessionRequest request);
    SessionResult pollSession(String providerSessionId);
    void cancelSession(String providerSessionId);
}
```
Only `BlocksAdapter` implements this currently. New AI providers must implement this interface.

---

## Dependencies

| Dependency | Purpose |
|-----------|---------|
| `blocks_sessions` table | Session state persistence |
| `blocks_messages` table | Chat history storage |
| `github_pull_requests` table | PR outcome tracking |
| `activity_events` table | State change audit log |
| `BlocksAdapter` | Blocks REST API client |
| Redis | Distributed polling locks (per session) |
| `SessionPollingScheduler` | 5-second polling loop (backend-worker only) |
| `AutomationService` | Triggered on terminal states |
| `GitHubAppService` | PR creation via GitHub API |

---

## Session State Machine

```
CREATED → PLANNING → EXECUTING → WAITING_REVIEW → PR_OPENED → DONE
                                                  ↓
                                           FAILED / CANCELED
```

| State | Meaning |
|-------|---------|
| `CREATED` | Session created, not yet picked up by Blocks |
| `PLANNING` | AI is analyzing the issue and planning approach |
| `EXECUTING` | AI is writing code |
| `WAITING_REVIEW` | AI completed work, waiting for human to review |
| `PR_OPENED` | Pull request created on GitHub |
| `DONE` | Session completed successfully |
| `FAILED` | Terminal failure (error from Blocks or polling exceeded max age) |
| `CANCELED` | Manually canceled by user |

Every transition:
1. Updates `blocks_sessions.execution_state`
2. Logs `ActivityEvent` (type: `AI_STATE_CHANGED`)
3. On terminal state: triggers `AutomationService.evaluate(BLOCKS_SESSION_COMPLETED)`

---

## Polling Architecture

```
backend-worker (single instance)
└── SessionPollingScheduler (@Scheduled every 5s)
      └── SessionPollService.pollActiveSessions()
            └── For each active BlocksSession:
                  1. Acquire Redis lock (key: "session-lock:{id}")
                  2. BlocksAdapter.pollSession(providerSessionId)
                  3. SessionPollTransactions.applyUpdate(session, result)
                     - Persist new state
                     - Persist new messages (offset-based deduplication)
                     - Create GitHub PR if PR URL present
                  4. Release lock
```

**Backoff configuration (`application.yml`):**
```yaml
mesha:
  polling:
    interval-ms: 5000
    backoff:
      base-ms: 5000
      max-ms: 300000   # 5 minutes max backoff
      multiplier: 2.0
    max-session-age-hours: 24
```

Sessions older than `max-session-age-hours` with non-terminal state are automatically marked `FAILED`.

---

## Important Business Rules

1. **Single worker instance:** The polling loop runs in `backend-worker` only (`APP_WORKER_ENABLED=true`). `backend-api` must never run `@Scheduled` jobs.

2. **Redis distributed lock:** Before polling a session, the worker acquires a Redis lock. This prevents duplicate polls if the worker restarts mid-cycle. Lock TTL = current backoff interval.

3. **Message offset deduplication:** `blocks_messages` has an `api_message_offset` field. Only messages with offsets greater than the last persisted offset are inserted. This prevents duplicate messages on retries.

4. **Human-in-the-loop:** The AI agent creates a Pull Request but never merges it. A human must review and approve the merge.

5. **ProviderAdapter abstraction:** Never call the Blocks HTTP API directly from business logic. Always go through `ProviderAdapter → BlocksAdapter`.

6. **Workspace-scoped API key:** The Blocks API key is retrieved from `workspace_blocks_config` and decrypted at runtime. One key per workspace.

7. **Session instructions:** When starting a session, the `instructions` field from the `BlocksSession` is sent to Blocks as the agent's task context.

8. **Max session age:** Sessions stuck in non-terminal state for longer than 24 hours are automatically failed to prevent resource leaks.

---

## Components That Must Not Be Modified Casually

- `SessionPollingScheduler` — the main scheduling loop; changing the `@Scheduled` interval affects all workspaces globally
- `SessionPollTransactions` — manages DB writes and GitHub PR creation atomically; partial writes can corrupt session state
- `ProviderAdapter` interface — any change requires updating `BlocksAdapter` and all tests
- `blocks_sessions.execution_state` enum — adding states requires a Flyway migration AND updates to the state machine logic
- Redis lock key format — changing it while sessions are active will create orphaned locks

---

## Related Feature Specifications

- [docs/features/ai-agent-assignment.md](../features/ai-agent-assignment.md)
- [docs/features/ai-session-monitoring.md](../features/ai-session-monitoring.md)
- [docs/features/agent-definitions.md](../features/agent-definitions.md)
