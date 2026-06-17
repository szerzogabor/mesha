# Automation & Rules

## Purpose

Enable no-code workflow automation within projects. Automation rules respond to system events (PR merged, AI session completed, etc.) by automatically updating issue state. Ticket rules impose conditional restrictions that prevent invalid transitions.

---

## Responsibilities

**Automation Rules:**
- Evaluate trigger conditions synchronously on system state changes
- Execute configured actions (status change, label add/remove, user assign)
- Support multiple actions per rule with optional per-action conditions

**Ticket Rules:**
- Evaluate conditions (AND logic) against the current issue state before an action
- Block invalid transitions (e.g., starting AI session on an issue with a specific status)
- Prevent status moves when restrictions apply

---

## Public Interfaces

### Automation Rules

| Method | Path | Description |
|--------|------|-------------|
| `GET/POST` | `/api/projects/{id}/automation-rules` | List / create rules |
| `GET/PATCH/DELETE` | `/api/projects/{id}/automation-rules/{ruleId}` | Get / update / delete rule |

### Ticket Rules

| Method | Path | Description |
|--------|------|-------------|
| `GET/POST` | `/api/projects/{id}/ticket-rules` | List / create ticket rules |
| `GET/PATCH/DELETE` | `/api/projects/{id}/ticket-rules/{ruleId}` | Get / update / delete rule |

---

## Dependencies

| Dependency | Purpose |
|-----------|---------|
| `automation_rules` table | Rule definitions |
| `automation_rule_actions` table | Actions per rule |
| `automation_action_conditions` table | Conditions on individual actions |
| `ticket_rules` table | Restriction rule definitions |
| `ticket_rule_conditions` table | AND-logic conditions |
| `ticket_rule_restrictions` table | Restrictions to enforce |
| `AutomationService` | Rule evaluation engine |
| `TicketRuleService` | Restriction enforcement engine |
| `IssueService` | Called by AutomationService to apply actions |
| `ActivityService` | Logs actions taken by automation |

---

## Automation Rule Model

```
AutomationRule
├── trigger_type:   PR_CREATED | PR_MERGED | BLOCKS_SESSION_COMPLETED
│                   | ISSUE_CREATED | STATUS_CHANGED
├── trigger_value:  (optional context, e.g. specific status name)
└── AutomationRuleActions[]
      ├── action_type:   MOVE_TO_STATUS | ADD_LABEL | REMOVE_LABEL | ASSIGN_USER
      ├── action_value:  target status name, label name, or user ID
      └── AutomationActionConditions[]  (AND logic)
            ├── condition_type:   HAS_STATUS | HAS_LABEL
            └── condition_value:  status or label to match
```

---

## Ticket Rule Model

```
TicketRule
├── name
├── description
├── TicketRuleConditions[]  (ALL must match — AND logic)
│     ├── condition_type:   HAS_STATUS | HAS_LABEL
│     └── condition_value:  status or label value
└── TicketRuleRestrictions[]
      ├── restriction_type: CANNOT_START_AI_SESSION | CANNOT_MOVE_TO_STATUS
      └── restriction_value: (optional, e.g. target status for CANNOT_MOVE_TO_STATUS)
```

---

## Important Business Rules

1. **Synchronous evaluation:** `AutomationService.evaluate()` is called **synchronously** from the service layer immediately after the triggering state change is persisted. Rules execute in the same transaction or immediately after. There is no async queue.

2. **Rule ordering:** Multiple automation rules on the same trigger are evaluated in the order they were created (by `created_at`). There is no explicit priority.

3. **Circular rule prevention:** Automation actions (e.g., `MOVE_TO_STATUS`) can themselves trigger further rules. This could create infinite loops. The current implementation does not have a cycle-detection mechanism — rule authors must avoid circular configurations.

4. **Ticket rule enforcement timing:** `TicketRuleService.checkRestrictions()` is called **before** the restricted action. If any restriction applies, an exception is thrown and the action is aborted. The issue is not modified.

5. **AND logic for conditions:** All conditions in a `TicketRule` must be satisfied simultaneously for the restrictions to apply.

6. **Action logging:** Every action taken by an automation rule generates an `ActivityEvent` so users can see which rule triggered which change.

7. **Project-scoped rules:** Both automation rules and ticket rules are scoped to a project. They do not apply across projects.

---

## Components That Must Not Be Modified Casually

- `AutomationService.evaluate()` — call sites in `IssueService`, `BlocksSessionService`, `GitHubWebhookService` must all pass the correct trigger type and context
- `TicketRuleService.checkRestrictions()` — must be called before any restricted action; skipping it bypasses all ticket rules
- Rule condition evaluation logic — changing AND to OR or vice versa is a breaking change for all existing rules

---

## Related Feature Specifications

- [docs/features/automation-rules.md](../features/automation-rules.md)
- [docs/features/ticket-rules.md](../features/ticket-rules.md)
