# Issue Management

## Purpose

Issues (tickets) are the core unit of work in Mesha. Users create, update, prioritize, and assign issues. AI agents are assigned to issues to implement them autonomously.

---

## Functional Requirements

1. Authenticated workspace members can create issues in a project.
2. Issues have a title, optional description, status, priority, assignee, and labels.
3. Issues get a human-readable ID on creation (e.g., `TP-42`).
4. Issues can be updated (title, description, status, priority, assignee, labels).
5. Status transitions fire automation rules and are logged as activity events.
6. Issues can be deleted (soft or hard, depending on implementation).
7. Issues can be listed with filters (status, priority, label, assignee) and pagination.
8. Issues can be viewed in Kanban (grouped by status) or List view.
9. Real-time updates arrive via SSE when other users modify issues.

---

## Business Rules

- Issue status must reference a valid `project_statuses` entry for that project.
- Priority is one of: `LOW`, `MEDIUM`, `HIGH`, `URGENT`.
- Every status change must:
  1. Persist the new status
  2. Log an `ActivityEvent` (type: `STATUS_CHANGED`, `old_value`, `new_value`)
  3. Call `TicketRuleService.checkRestrictions()` before persisting
  4. Call `AutomationService.evaluate(STATUS_CHANGED, ...)` after persisting
- `VIEWER` role members can read but not create or update issues.
- An issue can have one human assignee and one AI agent assignee simultaneously.
- Human-readable IDs are sequential per project and never reused.

---

## Validation Rules

- `title`: required, 1–500 characters.
- `description`: optional, max 10,000 characters.
- `status`: must be a valid status name for the project.
- `priority`: must be one of `LOW`, `MEDIUM`, `HIGH`, `URGENT`.
- `assigneeId`: optional, must be a workspace member's user ID.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Invalid status for project | `400 Bad Request` |
| Ticket rule blocks status change | `409 Conflict` with reason |
| Ticket rule blocks AI session start | `409 Conflict` with reason |
| Non-member accessing issue | `403 Forbidden` |
| Issue not found | `404 Not Found` |

---

## Dependencies

- `issues` table
- `activity_events` table
- `project_statuses` table
- `issue_labels`, `labels` tables
- `IssueService`, `IssueController`
- `ActivityService`
- `AutomationService`
- `TicketRuleService`

---

## Acceptance Criteria

- An issue is created with a unique readable ID.
- Status change logs an activity event with old and new values.
- Status change is blocked if a ticket rule restriction applies.
- Filtering by status, priority, and label returns correct results.
- SSE stream delivers updates within 1 second of a change.

---

## Regression Risks

- Removing `TicketRuleService.checkRestrictions()` call before status change bypasses all restrictions.
- Removing `AutomationService.evaluate()` call after status change breaks all `STATUS_CHANGED` automation rules.
- Removing `ActivityService.log()` call breaks the audit trail.
- Changing the readable ID generation sequence can create gaps or duplicates.
