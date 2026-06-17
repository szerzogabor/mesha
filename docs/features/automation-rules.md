# Automation Rules

## Purpose

Allow project administrators to configure no-code workflows that automatically update issue state in response to system events — reducing manual toil for repetitive status transitions, label assignments, and user assignments.

---

## Functional Requirements

1. `ADMIN` and `OWNER` members can create, update, and delete automation rules for a project.
2. Each rule has a trigger type (e.g., `PR_MERGED`) and one or more actions.
3. Each action can optionally have conditions that must all match (AND logic) before the action executes.
4. Rules are evaluated automatically when the triggering event occurs — no user action required.
5. Multiple rules can share the same trigger type; all matching rules execute.

---

## Business Rules

- Rules are project-scoped; they do not apply across projects.
- Rules are evaluated synchronously immediately after the triggering event is persisted.
- Evaluation order: rules are processed in creation order (`created_at` ascending).
- A rule fires all its actions where all action conditions match.
- Rule actions that fail do not roll back previous actions in the same rule evaluation.
- Circular rule configurations (e.g., `MOVE_TO_STATUS` triggering `STATUS_CHANGED` which triggers another `MOVE_TO_STATUS`) are not prevented — rule authors must avoid them.

### Supported Triggers

| Trigger | When it fires |
|---------|--------------|
| `PR_CREATED` | A GitHub PR is opened linked to an issue |
| `PR_MERGED` | A GitHub PR is merged |
| `BLOCKS_SESSION_COMPLETED` | An AI session reaches `DONE` state |
| `ISSUE_CREATED` | A new issue is created |
| `STATUS_CHANGED` | An issue's status changes |

### Supported Actions

| Action | Effect |
|--------|--------|
| `MOVE_TO_STATUS` | Change issue status to `action_value` |
| `ADD_LABEL` | Add label named `action_value` to issue |
| `REMOVE_LABEL` | Remove label named `action_value` from issue |
| `ASSIGN_USER` | Assign user with ID `action_value` to issue |

### Action Conditions

| Condition | Matches when |
|-----------|-------------|
| `HAS_STATUS` | Issue currently has status equal to `condition_value` |
| `HAS_LABEL` | Issue has label named `condition_value` |

---

## Validation Rules

- `trigger_type`: required, must be one of the supported triggers.
- At least one action per rule.
- `action_type`: must be one of the supported action types.
- `action_value`: required for all action types.
- `condition_type`: must be `HAS_STATUS` or `HAS_LABEL`.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| `MOVE_TO_STATUS` target status does not exist | Action skipped, error logged |
| `ADD_LABEL` label does not exist | Action skipped, error logged |
| `ASSIGN_USER` user is not a workspace member | Action skipped, error logged |
| Rule creates a circular loop | Potential infinite loop — not detected automatically |

---

## Dependencies

- `automation_rules`, `automation_rule_actions`, `automation_action_conditions` tables
- `AutomationService`, `AutomationRuleService`
- `AutomationRuleController`
- Called by: `IssueService`, `BlocksSessionService`, `GitHubWebhookService`

---

## Acceptance Criteria

- Creating an automation rule fires the correct actions when the trigger event occurs.
- An action with conditions only fires when ALL conditions match.
- Multiple rules with the same trigger all execute (in creation order).
- Actions executed by automation are visible in the issue activity feed.

---

## Regression Risks

- Removing `AutomationService.evaluate()` calls at trigger sites breaks all automation.
- Changing condition logic from AND to OR breaks existing rules with multiple conditions.
- Changing trigger type enum values without migration breaks existing rules.
