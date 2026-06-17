# Ticket Rules

## Purpose

Allow project administrators to define conditional restrictions that prevent invalid issue transitions — for example, blocking AI agent assignment on issues that are already completed, or preventing status moves that violate the project workflow.

---

## Functional Requirements

1. `ADMIN` and `OWNER` members can create, update, and delete ticket rules for a project.
2. Each rule has a set of conditions (AND logic) and a set of restrictions.
3. Before a restricted action is taken on an issue, the system checks all ticket rules.
4. If any rule's conditions match and the action is restricted, the action is blocked with a reason.

---

## Business Rules

- Rules are project-scoped.
- All conditions in a rule must match simultaneously (AND logic) for restrictions to apply.
- Restrictions are checked **before** the action is executed.
- If any rule blocks the action, the action is aborted entirely (no partial execution).
- Multiple rules can apply to the same action; the first matching rule that blocks the action wins.

### Supported Conditions

| Condition | Matches when |
|-----------|-------------|
| `HAS_STATUS` | Issue currently has the specified status |
| `HAS_LABEL` | Issue has the specified label |

### Supported Restrictions

| Restriction | Blocks |
|-------------|--------|
| `CANNOT_START_AI_SESSION` | Starting a new AI agent session on the issue |
| `CANNOT_MOVE_TO_STATUS` | Moving the issue to the specified target status |

---

## Validation Rules

- At least one condition per rule.
- At least one restriction per rule.
- `condition_type`: must be `HAS_STATUS` or `HAS_LABEL`.
- `restriction_type`: must be `CANNOT_START_AI_SESSION` or `CANNOT_MOVE_TO_STATUS`.
- `restriction_value`: required for `CANNOT_MOVE_TO_STATUS` (the target status to block).

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Rule blocks AI session start | `409 Conflict` with rule name/reason in response |
| Rule blocks status change | `409 Conflict` with rule name/reason in response |
| Frontend Kanban drag blocked | Card snaps back; toast shows reason |

---

## Dependencies

- `ticket_rules`, `ticket_rule_conditions`, `ticket_rule_restrictions` tables
- `TicketRuleService`, `TicketRuleController`
- Called by: `IssueService.transitionStatus()`, `BlocksSessionService.startSession()`

---

## Acceptance Criteria

- A ticket rule correctly blocks a status change when all its conditions match.
- A ticket rule correctly blocks AI session start when all its conditions match.
- A rule with two conditions only fires when BOTH conditions match simultaneously.
- The API response when blocked includes the rule name and reason.
- Deleting a rule immediately removes its restrictions.

---

## Regression Risks

- Removing `TicketRuleService.checkRestrictions()` call before status change bypasses all restrictions.
- Removing `TicketRuleService.checkRestrictions()` call before AI session start bypasses all restrictions.
- Changing AND logic to OR breaks existing multi-condition rules.
