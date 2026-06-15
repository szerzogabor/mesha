# GitHub Integration Architecture

## 1. GitHub App (Recommended over PAT tokens)

**Benefits:**
- Better security
- Repository-scoped permissions
- Webhook support
- Organization support
- Easier auditing

---

## 2. Required Permissions

| Permission | Level |
|---|---|
| Contents | Read/Write |
| Pull Requests | Read/Write |
| Issues | Read/Write |
| Metadata | Read |
| Webhooks | — |

---

## 3. Webhook Events

Required events:
- `pull_request`
- `push`
- `issue_comment`
- `issues`
- `workflow_run`
- `check_suite`

All webhooks must be signed and verified by the backend before processing.

---

## 4. Pull Request Sync Flow

```
GitHub
   ↓ webhook
backend-api (github webhook handler)
   ↓
PR Metadata Persisted
   ↓
Issue Updated (linked PR)
   ↓
WebSocket → Frontend Notification
```

---

## 5. Repository Linking

Users connect their GitHub organization via the GitHub App OAuth flow:

```
User → /settings/github/connect
    → GitHub App Install
    → Callback → Store installation ID + token
    → Repository list available for project linking
```
