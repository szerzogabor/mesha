# AI Agent Assignment

## Purpose

Enable users to assign an issue to an AI agent (via Blocks), initiating an automated implementation session that produces a GitHub Pull Request for human review.

---

## Functional Requirements

1. A user clicks "Assign to AI" on an issue to start a Blocks session.
2. The system checks ticket rules before starting the session.
3. A `BlocksSession` is created with state `CREATED`.
4. The backend calls the Blocks API to start an agent session with the issue details.
5. The worker polls the session every 5 seconds and updates its state.
6. The frontend displays the session state, chat messages, and resulting PR.
7. The user can cancel an active session.
8. Multiple sessions can exist per issue (history is preserved).

---

## Business Rules

- Ticket rules may prevent starting an AI session (e.g., issue must be in a specific status).
- Only one session can be in a non-terminal state per issue at a time.
- The AI agent creates a GitHub PR but never merges it.
- Session state progresses through: `CREATED → PLANNING → EXECUTING → WAITING_REVIEW → PR_OPENED → DONE`.
- Terminal states: `DONE`, `FAILED`, `CANCELED`.
- Sessions stuck in non-terminal state for > 24 hours are automatically marked `FAILED`.
- Activity events are logged for every state transition (`AI_ASSIGNED`, `AI_STATE_CHANGED`, `AI_PR_OPENED`, `AI_COMPLETED`, `AI_FAILED`, `AI_CANCELED`).

---

## Validation Rules

- Issue must exist and belong to the user's workspace.
- No other non-terminal session may be active for the issue.
- Ticket rules must not block AI session start.
- The workspace must have a valid Blocks API key configured.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| No Blocks API key configured | `400 Bad Request` with setup instruction |
| Ticket rule blocks session start | `409 Conflict` with rule reason |
| Active session already exists | `409 Conflict` |
| Blocks API unavailable | Session marked `FAILED` after retry exhaustion |
| Session exceeds max age | Session marked `FAILED` automatically |

---

## Dependencies

- `blocks_sessions` table
- `blocks_messages` table
- `BlocksSessionService`, `BlocksSessionController`
- `BlocksAdapter` (via `ProviderAdapter`)
- `SessionPollingScheduler` (backend-worker)
- `SessionPollService`, `SessionPollTransactions`
- `TicketRuleService`
- `ActivityService`
- `AutomationService` (on terminal state)
- `workspace_blocks_config` table
- `AISessionsPanel.tsx`, `AssignToBlocksButton.tsx` (frontend)

---

## Acceptance Criteria

- Clicking "Assign to AI" creates a session and transitions the issue activity feed.
- Session state updates in the UI within 10 seconds of a change.
- The resulting PR link appears in the issue when `PR_OPENED` state is reached.
- Canceling a session sets it to `CANCELED` and logs the activity event.
- A second "Assign to AI" click is blocked while a session is active.

---

## Regression Risks

- Removing `TicketRuleService.checkRestrictions()` before session start bypasses restrictions.
- Removing the active-session guard allows multiple concurrent sessions per issue.
- Changing the state machine order causes incorrect status displays.
- Removing `AutomationService.evaluate()` on terminal state breaks post-completion automation.
