# Pull Request Tracking

## Purpose

Track the lifecycle of GitHub Pull Requests created by AI agents so that users can see PR status directly within Mesha and automation rules can react to PR events.

---

## Functional Requirements

1. When an AI session produces a PR, the PR is recorded in `github_pull_requests`.
2. PR state (`OPEN`, `MERGED`, `CLOSED`) is updated via GitHub webhooks.
3. PRs are listed per workspace and linked to their source issue via the `BlocksSession`.
4. Users can manually trigger a PR sync via `POST /api/pull-requests/sync`.
5. A PR link is displayed in the issue detail view.

---

## Business Rules

- A `GitHubPullRequest` is always linked to a `BlocksSession` (never orphaned).
- PR records are deduplicated by `(session_id, pr_number)` — unique constraint (V37 migration).
- PR state changes trigger `AutomationService` evaluation (`PR_CREATED`, `PR_MERGED`).
- Mesha never merges PRs — only GitHub can perform the merge.
- Closed-without-merge PRs are tracked as `CLOSED` state but do not trigger `PR_MERGED`.

---

## Validation Rules

- `prNumber`: positive integer, unique per `session_id`.
- `state`: must be one of `OPEN`, `MERGED`, `CLOSED`.
- `url`: must be a valid GitHub PR URL.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Duplicate PR record (same session + PR number) | Upsert (update existing, no duplicate) |
| GitHub webhook for unknown PR | Logged and ignored |
| PR sync for non-existent session | `404 Not Found` |

---

## Dependencies

- `github_pull_requests` table (unique constraint in V37)
- `blocks_sessions` table (FK: `session_id`)
- `GitHubPullRequestService`, `GitHubPullRequestController`
- `GitHubWebhookService` (updates PR state on webhook)
- `AutomationService` (triggered on PR events)
- `PullRequestRow.tsx` (frontend display)

---

## Acceptance Criteria

- After an AI session completes, a PR link appears in the Mesha issue.
- Merging the PR on GitHub causes the `PR_MERGED` automation trigger to fire.
- Manual sync reflects the current PR state from GitHub within seconds.
- Duplicate webhook deliveries do not create duplicate PR records.

---

## Regression Risks

- Removing the unique constraint from V37 allows duplicate PR records.
- Removing the `PR_MERGED` trigger from webhook processing breaks all merge automation.
- Changing the PR-to-session link allows orphan PRs.
