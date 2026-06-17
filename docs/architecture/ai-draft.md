# AI Draft Generation

## Purpose

Allow users to describe a feature or task in natural language and have an AI (Anthropic Claude) generate a structured issue draft — including title, description, acceptance criteria, labels, priority, and implementation notes — before committing it as a tracked issue.

---

## Responsibilities

- Accept a natural language prompt from the user
- Call Anthropic Claude API to generate structured ticket content
- Persist the draft in `ai_drafts` with status tracking
- Support regeneration of failed or unsatisfactory drafts
- Allow user to approve (creates an Issue) or reject (deletes draft)
- Fire `ActivityEvent` (type: `ISSUE_CREATED_FROM_AI_DRAFT`) on approval

---

## Public Interfaces

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/ai/drafts` | Generate a new AI draft from a prompt |
| `GET` | `/api/ai/drafts/{id}` | Get draft details |
| `POST` | `/api/ai/drafts/{id}/approve` | Approve draft → create Issue |
| `POST` | `/api/ai/drafts/{id}/regenerate` | Regenerate draft with same prompt |

### Request: Generate Draft
```json
{
  "projectId": "uuid",
  "prompt": "Add a rate limiter to the login endpoint"
}
```

### Response: AI Draft
```json
{
  "id": "uuid",
  "status": "COMPLETED",
  "prompt": "...",
  "generatedTitle": "Add rate limiting to login endpoint",
  "generatedDescription": "...",
  "generatedAcceptanceCriteria": "...",
  "suggestedLabels": ["security", "backend"],
  "suggestedPriority": "HIGH",
  "implementationNotes": "..."
}
```

---

## Dependencies

| Dependency | Purpose |
|-----------|---------|
| `ai_drafts` table | Draft persistence |
| `ClaudeAIAdapter` | Anthropic Claude API client |
| `AIOrchestrationService` | Routes generation requests to the right adapter |
| `IssueService` | Creates the Issue on approval |
| `ActivityService` | Logs `ISSUE_CREATED_FROM_AI_DRAFT` |
| `ANTHROPIC_API_KEY` env var | API authentication |

---

## Important Business Rules

1. **Draft statuses:** `PENDING` → `COMPLETED` | `FAILED`. On approval: `APPROVED`. On rejection: `REJECTED`. Only `COMPLETED` drafts can be approved or regenerated.

2. **Approval creates issue:** Approving a draft calls `IssueService.createIssue()` with the generated content pre-filled. The draft `status` is set to `APPROVED` and cannot be modified again.

3. **Regeneration:** Creates a new `AIDraft` row (does not overwrite the previous one) using the same prompt. The old draft remains in `COMPLETED` state.

4. **Claude model:** Configured via `ANTHROPIC_MODEL` env var (default: `claude-3-5-sonnet-20241022`). Never hardcode the model name.

5. **Prompt injection risk:** User-provided prompts are passed to the LLM. Do not include other users' data or workspace secrets in the prompt context.

---

## Related Feature Specifications

- [docs/features/ai-draft.md](../features/ai-draft.md)
