# AI Draft Generation

## Purpose

Allow users to describe a feature or bug in natural language and receive a structured, well-formed issue draft from an AI (Anthropic Claude) before committing it as a tracked issue.

---

## Functional Requirements

1. Any workspace member with `DEVELOPER` role or higher can generate an AI draft.
2. The user provides a free-text prompt describing the desired feature or task.
3. The AI generates: title, description, acceptance criteria, suggested labels, priority, and implementation notes.
4. The draft is displayed for review in a modal before commitment.
5. The user can approve the draft (creating an issue) or reject it (discarding it).
6. The user can regenerate the draft if unsatisfied with the result.
7. Approved drafts create a new issue pre-filled with the generated content.

---

## Business Rules

- Draft generation is non-blocking: the UI may show a loading state while the AI responds.
- Only `COMPLETED` drafts can be approved or regenerated. `FAILED` drafts can only be regenerated.
- Approving a draft is idempotent: calling approve twice returns the same issue (no duplicates).
- The draft remains visible after approval for audit purposes (`status: APPROVED`).
- `REJECTED` drafts are kept in the database but are not visible in the UI.
- The AI model used is configured via `ANTHROPIC_MODEL` environment variable — never hardcoded.
- User prompts must not include secrets or PII — the backend does not redact prompts before sending to Claude.

---

## Validation Rules

- `prompt`: required, 1–5000 characters.
- `projectId`: required, must be a valid project the user has access to.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Anthropic API unavailable | Draft status set to `FAILED`; user shown error |
| Prompt exceeds max length | `400 Bad Request` |
| Approve a `FAILED` draft | `409 Conflict` |
| Approve an already `APPROVED` draft | Returns existing issue (idempotent) |

---

## Dependencies

- `ai_drafts` table
- `AIDraftService`, `AIDraftController`
- `ClaudeAIAdapter` → Anthropic API
- `AIOrchestrationService`
- `IssueService` (called on approval)
- `ActivityService` (logs `ISSUE_CREATED_FROM_AI_DRAFT`)
- `AIDraftModal.tsx` (frontend)

---

## Acceptance Criteria

- A user can submit a prompt and receive a structured draft within 30 seconds.
- The draft modal shows all generated fields (title, description, acceptance criteria, labels, priority).
- Approving the draft creates an issue with the generated content pre-filled.
- Regenerating produces a new draft from the same prompt without modifying the original.
- Draft status transitions are correct: `PENDING → COMPLETED → APPROVED`.

---

## Regression Risks

- Changing the Anthropic response parsing breaks all draft generation.
- Removing the idempotency check on approval can create duplicate issues.
- Changing the `status` enum values breaks status checks throughout `AIDraftService`.
