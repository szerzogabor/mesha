# Authentication & Authorization

## Purpose

Control who can access the system and what they are permitted to do. Mesha uses Clerk for identity management, JWT for stateless API authentication, and a workspace-scoped role system (RBAC) for authorization.

---

## Responsibilities

- Validate Clerk-issued JWT tokens on every API request
- Enforce workspace role-based access control (RBAC)
- Validate HMAC signatures on incoming webhooks
- Generate GitHub App JWT tokens for GitHub API calls
- Sync Clerk users into the local `users` table

---

## Public Interfaces

### Spring Security Filter Chain
All requests pass through JWT validation before reaching controllers.
- **Protected:** all endpoints except `/api/webhooks/*`
- **Principal:** `@AuthenticationPrincipal` injects the resolved `User` entity

### Auth Endpoint
```
POST /api/auth/sync
Authorization: Bearer <Clerk JWT>
```
Creates or updates the local user record from the Clerk JWT claims.

### Webhook Endpoints (no auth, HMAC validated)
```
POST /api/webhooks/blocks   — Blocks webhook (HMAC-SHA256)
POST /api/webhooks/github   — GitHub webhook (HMAC-SHA256)
```

---

## Dependencies

| Dependency | Purpose |
|-----------|---------|
| **Clerk** | Issues JWTs, hosts JWKS endpoint |
| `spring-security-oauth2-resource-server` | JWT validation via JWKS |
| `jjwt` | GitHub App JWT generation (RS256) |
| `workspace_members` table | Stores roles per workspace per user |
| `users` table | Local user records synced from Clerk |
| `github_installations` table | GitHub App installation per workspace |

---

## Important Business Rules

1. **JWT validation:** Every non-webhook request must carry a valid Clerk JWT. The token is validated against Clerk's JWKS endpoint (`CLERK_JWKS_URI`). Expired or invalid tokens return HTTP 401.

2. **User sync:** Clerk user data is authoritative. The `POST /api/auth/sync` endpoint creates or updates the local `users` row from JWT claims. This must be called at least once before a user can join a workspace.

3. **Workspace roles:**
   - `OWNER` — full access including workspace deletion and member management
   - `ADMIN` — can manage projects, labels, automation rules, and integrations
   - `DEVELOPER` — can create/update issues, trigger AI sessions
   - `VIEWER` — read-only access

4. **Webhook HMAC:** Both Blocks and GitHub webhooks are validated with HMAC-SHA256 before any processing. Invalid signatures return HTTP 403 without logging the payload.

5. **GitHub App JWT:** The GitHub App private key (RSA) is stored as an environment variable. A short-lived JWT (10 min) is generated per request to the GitHub API. Installation tokens (1 hour) are exchanged for repository operations.

6. **Blocks API key:** Stored encrypted (AES-256) in `workspace_blocks_config.blocks_api_key`. The encryption key comes from `BLOCKS_ENCRYPTION_SECRET` environment variable. Never store or log the plaintext key.

---

## Components That Must Not Be Modified Casually

- `SecurityConfig` — changes to the filter chain can expose all endpoints
- `JwtAuthenticationConverter` — changes affect how the principal is resolved
- `BlocksWebhookService.validateSignature()` / `GitHubWebhookService.validateSignature()` — disabling these opens webhook forgery attacks
- `BlocksEncryptionService` — changing encryption scheme without migration corrupts all stored API keys

---

## Related Feature Specifications

- [docs/features/user-registration.md](../features/user-registration.md)
- [docs/features/login.md](../features/login.md)
- [docs/features/workspace-management.md](../features/workspace-management.md)
