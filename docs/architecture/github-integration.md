# GitHub Integration

## Purpose

Connect Mesha workspaces to GitHub repositories so that AI-generated code can be submitted as Pull Requests, and PR lifecycle events (opened, merged, closed) trigger automation rules.

---

## Responsibilities

- Manage GitHub App installations per workspace (OAuth flow)
- Sync available repositories from GitHub installations
- Create Pull Requests via GitHub App on behalf of AI sessions
- Process incoming GitHub webhooks (PR events)
- Track `GitHubPullRequest` state in the database
- Link PRs to `BlocksSessions`
- Trigger `AutomationService` on PR events

---

## Public Interfaces

### REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/github/installations` | List GitHub App installations for workspace |
| `GET` | `/api/pull-requests` | List PRs for workspace |
| `POST` | `/api/pull-requests/sync` | Sync PR state from GitHub |
| `GET` | `/api/workspaces/{id}/github/repositories` | List connected repositories |

### OAuth Callback
```
GET /github/callback   (frontend route, not API)
```
Receives the OAuth code from GitHub and exchanges it for an installation token.

### Webhook Endpoint
```
POST /api/webhooks/github
```
No authentication. HMAC-SHA256 signature validated against `GITHUB_WEBHOOK_SECRET`.

---

## Dependencies

| Dependency | Purpose |
|-----------|---------|
| `github_installations` table | Per-workspace installation records |
| `github_repositories` table | Connected repo records |
| `github_pull_requests` table | PR tracking |
| `github_webhook_events` table | Raw webhook payload log |
| `github_audit_logs` table | API call audit trail |
| `GitHubAppService` | GitHub App JWT generation + API calls |
| `GitHubWebhookService` | Webhook processing and signature validation |
| `GitHubPullRequestService` | PR state sync |
| `AutomationService` | Triggered on PR events |
| `blocks_sessions` table | Links PRs to AI sessions |
| `GITHUB_APP_ID` env var | GitHub App identifier |
| `GITHUB_APP_PRIVATE_KEY` env var | RSA private key for JWT signing |
| `GITHUB_CLIENT_SECRET` env var | OAuth client secret |
| `GITHUB_WEBHOOK_SECRET` env var | Webhook HMAC secret |

---

## Important Business Rules

1. **GitHub App JWT:** Short-lived (10 min), signed with the App's RSA private key (RS256). Generated on demand — never cached for longer than 9 minutes.

2. **Installation token:** Exchanged from the JWT for a 1-hour scoped token that authorizes operations on specific repositories. Cached with TTL.

3. **HMAC validation:** All GitHub webhook payloads are validated with HMAC-SHA256 against `GITHUB_WEBHOOK_SECRET` before processing. Invalid signatures are rejected with HTTP 400 without logging the full payload.

4. **Webhook idempotency:** Webhook events are logged to `github_webhook_events` by `delivery_id`. Duplicate deliveries (GitHub retry mechanism) must not create duplicate side effects.

5. **PR deduplication:** `github_pull_requests` has a unique constraint on `(session_id, pr_number)` (added in V37). Duplicate PR creation is rejected at the DB level.

6. **PR → Session link:** A `GitHubPullRequest` is always linked to a `BlocksSession`. No orphan PRs should be created.

7. **Human merge only:** Mesha tracks PRs but never merges them. The `PR_MERGED` automation trigger fires when GitHub sends the `pull_request.closed` event with `merged: true`.

8. **Repository sync:** `GET /api/pull-requests/sync` fetches current PR state from GitHub and updates local records. This is idempotent and safe to call multiple times.

9. **Audit logging:** Every GitHub API call is recorded in `github_audit_logs` with action and timestamp for compliance.

---

## Components That Must Not Be Modified Casually

- `GitHubWebhookService.validateSignature()` — disabling breaks security
- `github_webhook_events` deduplication logic — removing it causes double-processing
- `GitHubAppService.generateJwt()` — expiry must stay ≤ 10 min (GitHub requirement)
- Unique constraint in V37 migration — critical for PR deduplication

---

## Related Feature Specifications

- [docs/features/github-integration.md](../features/github-integration.md)
- [docs/features/pull-request-tracking.md](../features/pull-request-tracking.md)
