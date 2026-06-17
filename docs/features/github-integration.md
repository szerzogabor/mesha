# GitHub Integration

## Purpose

Connect a workspace to GitHub so that AI-generated code can be submitted as Pull Requests to real repositories, and PR lifecycle events can trigger automation rules.

---

## Functional Requirements

1. A workspace `ADMIN` or `OWNER` can install the Mesha GitHub App for their GitHub organization.
2. After installation, available repositories are synced and displayed in workspace settings.
3. When an AI session produces a PR, the PR is created on GitHub via the GitHub App.
4. PR state (open, merged, closed) is tracked in Mesha and kept in sync via webhooks.
5. PR events (merged, created) trigger configured automation rules.
6. Users can manually sync PR state via `POST /api/pull-requests/sync`.

---

## Business Rules

- GitHub App installation is per workspace. Multiple workspaces can install the same GitHub App.
- The GitHub App uses JWT authentication (RS256, 10 min expiry).
- Repository sync retrieves all repositories accessible to the installation.
- Only repositories connected to a workspace are shown in the UI.
- PR creation is triggered by `BlocksAdapter` when the AI session produces a PR URL.
- GitHub webhooks are validated with HMAC-SHA256 before any processing.
- Duplicate webhook deliveries (GitHub's retry mechanism) must not duplicate side effects.
- PR records in Mesha are keyed by `(session_id, pr_number)` — unique constraint enforced.
- Merging a PR is done on GitHub — Mesha never merges PRs.

---

## Validation Rules

- GitHub App installation ID must be valid and belong to the workspace.
- Repository must be accessible via the installation token.
- Webhook `X-Hub-Signature-256` must match `HMAC-SHA256(secret, payload)`.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Invalid webhook signature | `403 Forbidden`, no processing |
| Duplicate webhook delivery | Idempotent — no duplicate side effects |
| GitHub API rate limit | Retry with exponential backoff |
| No GitHub installation for workspace | `400 Bad Request` when trying to create PR |
| Duplicate PR record | DB unique constraint rejects duplicate; returns existing record |

---

## Dependencies

- `github_installations` table
- `github_repositories` table
- `github_pull_requests` table
- `github_webhook_events` table
- `github_audit_logs` table
- `GitHubAppService`, `GitHubWebhookService`, `GitHubPullRequestService`
- `GitHubAppController`, `GitHubWebhookController`, `GitHubPullRequestController`
- `AutomationService` (triggered on PR events)
- `GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_CLIENT_SECRET`, `GITHUB_WEBHOOK_SECRET` env vars

---

## Acceptance Criteria

- Installing the GitHub App makes repositories available in workspace settings.
- AI session completes and a PR is visible in the Mesha issue with a link to GitHub.
- Merging the PR on GitHub triggers the `PR_MERGED` automation rule (if configured).
- Manual sync (`/api/pull-requests/sync`) correctly updates PR state.
- Duplicate webhook deliveries do not create duplicate PR records.

---

## Regression Risks

- Removing HMAC validation opens webhook forgery attacks.
- Removing duplicate-delivery guard causes duplicate PRs or double automation execution.
- Changing PR unique constraint (V37) allows duplicates.
- JWT expiry > 10 min causes GitHub API rejections.
