# AI Session Monitoring

## Purpose

Give users real-time visibility into the progress of AI agent execution — including current state, chat messages between user and agent, execution timeline, and the resulting Pull Request.

---

## Functional Requirements

1. The issue detail page shows all sessions (past and current) for the issue.
2. Each session displays its current execution state and a state timeline.
3. The chat drawer shows messages exchanged between the user and the AI agent.
4. The resources panel shows the repository and branch the AI is working on.
5. Session state updates appear in the UI within 10 seconds (via polling or SSE).
6. Completed sessions show a link to the GitHub PR.
7. The activity feed shows all AI state change events (logged as `ActivityEvent`).

---

## Business Rules

- Sessions are never deleted — history is preserved indefinitely.
- Chat messages are stored in insertion order via `api_message_offset`.
- Only the most recent non-terminal session is shown as "active".
- Terminal sessions (DONE, FAILED, CANCELED) are shown in a collapsed history section.
- State display maps to human-readable labels: `PLANNING` → "Planning", `EXECUTING` → "Coding", etc.

---

## Validation Rules

- No user input validation — this is a read-only monitoring feature.
- Session access is scoped to the workspace (users cannot see sessions from other workspaces).

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Session not found | `404 Not Found` |
| Access to session in different workspace | `403 Forbidden` |
| SSE disconnection | Auto-reconnect; stale data re-fetched on reconnect |

---

## Dependencies

- `blocks_sessions` table
- `blocks_messages` table
- `activity_events` table
- `BlocksSessionService`, `BlocksSessionController`
- `BlocksMessageService`
- `AISessionsPanel.tsx`, `AIExecutionTimeline.tsx`, `SessionChatDrawer.tsx`
- `BlocksActivityFeed.tsx`, `ResourcesPanel.tsx`
- `useBlocksSessions()`, `useBlocksMessages()` hooks

---

## Acceptance Criteria

- Navigating to an issue with an active session immediately shows the current state.
- State transitions are reflected in the UI within 10 seconds.
- Chat messages display in chronological order without duplicates.
- Completed sessions show the PR link.
- Multiple past sessions are listed in reverse chronological order.

---

## Regression Risks

- Removing `api_message_offset` deduplication causes duplicate messages to appear.
- Changing the execution state enum display mapping shows wrong labels.
- Removing session workspace scope check exposes sessions cross-workspace.
