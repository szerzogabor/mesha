# Issue Links

## Purpose

Allow users to establish explicit relationships between issues — such as blocking dependencies, duplicates, or related work — to communicate and enforce issue dependencies.

---

## Functional Requirements

1. Users can create links between two issues.
2. Supported link types: `BLOCKS`, `BLOCKED_BY`, `RELATES_TO`, `DUPLICATES`, `DUPLICATED_BY`.
3. Links are displayed in the issue detail view under an "Issue Links" section.
4. Users can remove links.
5. Links are directional: creating `A BLOCKS B` automatically creates the reverse `B BLOCKED_BY A`.

---

## Business Rules

- Both issues must belong to the same workspace (cross-project links are allowed within a workspace).
- An issue cannot link to itself.
- Bidirectional link types (`BLOCKS`/`BLOCKED_BY`, `DUPLICATES`/`DUPLICATED_BY`) are stored as two rows (one for each direction).
- `RELATES_TO` is stored as a single row (symmetric relationship).
- Duplicate links (same source, target, and type) are rejected.

---

## Validation Rules

- `sourceIssueId`: required, must be accessible to the user.
- `targetIssueId`: required, must be in the same workspace, cannot equal `sourceIssueId`.
- `linkType`: must be one of `BLOCKS`, `BLOCKED_BY`, `RELATES_TO`, `DUPLICATES`, `DUPLICATED_BY`.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Self-link | `400 Bad Request` |
| Duplicate link | `409 Conflict` |
| Target issue in different workspace | `403 Forbidden` |
| Invalid link type | `400 Bad Request` |

---

## Dependencies

- `issue_links` table
- `IssueLinkService`, `IssueLinkController`
- `IssueLinksPanel.tsx` (frontend)

---

## Acceptance Criteria

- Creating `A BLOCKS B` shows `A blocks B` on A's page and `B is blocked by A` on B's page.
- Deleting the link removes it from both issues.
- `RELATES_TO` links appear on both issues without a directional label.

---

## Regression Risks

- Removing the bidirectional creation logic for `BLOCKS`/`BLOCKED_BY` causes asymmetric link display.
- Removing the self-link check allows nonsensical loops.
