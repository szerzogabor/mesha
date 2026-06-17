# Kanban Board

## Purpose

Provide a visual drag-and-drop interface for managing issues by status, allowing teams to see work in progress at a glance and move issues between stages efficiently.

---

## Functional Requirements

1. The Kanban board displays columns corresponding to a project's custom statuses (ordered by `position`).
2. Issues are displayed as cards within their current status column.
3. Users can drag and drop cards between columns to change the issue status.
4. A new status column can be added from within the Kanban board.
5. The board can be switched to a List view via a toggle.
6. The board filters issues by the active filter criteria (priority, label, assignee).
7. Real-time updates via SSE reflect changes from other users without page reload.

---

## Business Rules

- Column order matches the `position` field of `project_statuses`, ascending.
- Dropping a card on a column triggers a status change, which:
  1. Checks ticket rules (may be rejected)
  2. Persists the new status
  3. Fires automation rules
  4. Logs activity event
- Only `DEVELOPER`, `ADMIN`, and `OWNER` can drag cards (move issue status).
- `VIEWER` can see the board but cannot drag cards.
- The board does not implement swimlanes (issues are sorted by creation date within columns).

---

## Validation Rules

- Target status must be a valid status for the project.
- Ticket rule restrictions are enforced on drag-and-drop the same as on direct status update.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Ticket rule blocks the move | Toast error with reason; card snaps back |
| Network error on status update | Toast error; card snaps back to original column |
| SSE disconnection | Automatic reconnect; stale board data is re-fetched |

---

## Dependencies

- `@dnd-kit/core`, `@dnd-kit/sortable` (drag and drop)
- `useIssues()` hook (TanStack Query)
- `useProjectStatuses()` hook
- `useIssueEvents()` hook (SSE)
- `KanbanView.tsx`, `KanbanCard.tsx`, `AddStatusColumn.tsx`
- `PATCH /api/projects/{id}/issues/{issueId}` (status update)

---

## Acceptance Criteria

- Columns render in the correct order based on status `position`.
- Dragging a card to another column updates the issue's status and logs the change.
- If a ticket rule blocks the move, the card returns to its original column with an error message.
- Adding a new status column creates a `project_statuses` record and immediately appears on the board.
- Board updates in real-time when another user changes an issue's status.

---

## Regression Risks

- Changing the status `position` sort direction inverts column order.
- Removing the optimistic update on drag causes visual lag.
- Removing SSE invalidation means the board goes stale without page reload.
