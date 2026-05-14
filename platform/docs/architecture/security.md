# Security

## 1. Authentication

**Recommended providers:**
- Clerk
- Auth0

Both support OAuth2, JWTs, and SSO.

---

## 2. Authorization

RBAC roles: Owner, Admin, Developer, Viewer.

See [api-design.md](./api-design.md) for per-role capabilities.

---

## 3. AI Permission Boundaries

AI providers receive only scoped capabilities:

```
READ_ISSUES
WRITE_COMMENTS
CREATE_BRANCH
CREATE_PR
READ_REPOSITORY
```

**Never allow:**
- AI auto-merge to production
- Full organization admin permissions
- Frontend direct access to Blocks APIs

---

## 4. Webhook Security

All incoming GitHub webhooks must be verified using the HMAC signature (`X-Hub-Signature-256`) before processing.

---

## 5. Secrets Management

- All secrets stored in environment variables via Render / Vercel secrets — never committed to the repository
- GitHub App private key stored as an environment secret
- Database credentials rotated on breach

---

## 6. Rate Limiting

All public API endpoints protected by rate limiting (Redis-backed).

---

## 7. Audit Logging

All AI-triggered actions (session start, PR create, issue update) are logged with:
- timestamp
- actor (user ID or AI provider ID)
- action
- resource ID
- outcome
