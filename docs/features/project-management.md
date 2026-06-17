# Project Management

## Purpose

Projects are containers for issues within a workspace. Each project has its own custom statuses, automation rules, ticket rules, and issue backlog.

---

## Functional Requirements

1. Workspace `ADMIN` and `OWNER` can create projects within a workspace.
2. Projects have a name, optional description, and a unique key (e.g., `TP`).
3. Each project has a configurable set of custom statuses (ordered, named, colored).
4. A project's Kanban board displays issues grouped by status column.
5. Projects can be listed, updated, and deleted.
6. Deleting a project deletes all its issues, statuses, automation rules, and ticket rules.

---

## Business Rules

- Project keys are used to generate readable issue identifiers (e.g., `TP-42`).
- Project keys are uppercase, unique within a workspace, 2–10 characters.
- Each project starts with a default set of statuses (e.g., `To Do`, `In Progress`, `Done`).
- Statuses are ordered — order determines the Kanban column sequence.
- At least one status must exist per project.
- `VIEWER` role can read projects but cannot create or modify them.

---

## Validation Rules

- `name`: required, 1–100 characters.
- `key`: required, 2–10 uppercase alphanumeric characters, unique within workspace.
- Status `name`: required, 1–50 characters.
- Status `color`: optional, hex color code.
- Status `position`: non-negative integer, determines column order.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Duplicate project key in workspace | `409 Conflict` |
| Deleting the last status | `400 Bad Request` |
| Non-admin creating a project | `403 Forbidden` |

---

## Dependencies

- `projects` table
- `project_statuses` table
- `ProjectService`, `ProjectStatusService`
- `ProjectController`, `ProjectStatusController`
- `AutomationRuleService`, `TicketRuleService` (cascade delete)

---

## Acceptance Criteria

- A project can be created with a unique key and immediately contains default statuses.
- Statuses can be reordered, and the Kanban board reflects the new order immediately.
- Deleting a project also deletes all associated issues and rules.
- Project key is immutable after creation (changing it would break issue readable IDs).

---

## Regression Risks

- Allowing duplicate project keys breaks issue ID uniqueness.
- Removing the minimum-status check allows issues to have no valid status.
- Changing status position logic breaks Kanban column ordering.
