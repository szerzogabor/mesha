# API Design

## 1. Ticket APIs

```
POST   /api/issues
GET    /api/issues/{id}
PATCH  /api/issues/{id}
POST   /api/issues/{id}/comments
```

---

## 2. AI APIs

```
POST   /api/ai/ticket-drafts
POST   /api/issues/{id}/assign-blocks
GET    /api/issues/{id}/ai-sessions
```

---

## 3. GitHub APIs

```
POST   /api/github/connect
POST   /api/webhooks/github
GET    /api/issues/{id}/pull-requests
```

---

## 4. WebSocket Events

**Endpoint:** `ws://<host>/ws`

Events emitted by server:

| Event | Payload |
|---|---|
| `ai.session.started` | `{ issueId, sessionId }` |
| `ai.session.completed` | `{ issueId, sessionId }` |
| `pr.created` | `{ issueId, prUrl, prNumber }` |
| `pr.review_requested` | `{ issueId, prId }` |
| `issue.updated` | `{ issueId, changes }` |

---

## 5. Authentication

All API endpoints require a valid Bearer token (issued by Clerk or Auth0).

AI provider endpoints on the backend never expose credentials to the frontend.

---

## 6. Authorization (RBAC)

| Role | Capabilities |
|---|---|
| Owner | Full access |
| Admin | Manage team, projects, integrations |
| Developer | Create/edit issues, assign AI, review PRs |
| Viewer | Read-only access |

AI provider scoped capabilities (never grant more than needed):

```
READ_ISSUES
WRITE_COMMENTS
CREATE_BRANCH
CREATE_PR
READ_REPOSITORY
```
