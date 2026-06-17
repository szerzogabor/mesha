# Login

## Purpose

Authenticate existing users so they can access their workspaces and projects.

---

## Functional Requirements

1. Users log in via Clerk's hosted sign-in page (`/sign-in`).
2. Clerk issues a short-lived JWT on successful authentication.
3. The frontend attaches the JWT as `Authorization: Bearer <token>` on every API request.
4. The backend validates the JWT against Clerk's JWKS endpoint on every request.
5. After login, the frontend calls `POST /api/auth/sync` to ensure the local user record is up to date.

---

## Business Rules

- Sessions are managed by Clerk. Mesha has no session store.
- JWT tokens expire. The frontend (via Clerk SDK) automatically refreshes tokens.
- A user with a valid JWT but no local `users` record is created on the next `/api/auth/sync` call.
- There is no "logout" API endpoint — the frontend clears the Clerk session.

---

## Validation Rules

- JWT must be signed by Clerk's private key (validated via JWKS).
- JWT must not be expired.
- JWT `iss` (issuer) must match the configured Clerk issuer.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Missing `Authorization` header | `401 Unauthorized` |
| Expired JWT | `401 Unauthorized` |
| Invalid JWT signature | `401 Unauthorized` |
| JWT from wrong Clerk instance | `401 Unauthorized` |

---

## Dependencies

- Clerk (identity provider + JWKS endpoint)
- `spring-security-oauth2-resource-server`
- `SecurityConfig`, `JwtAuthenticationConverter`

---

## Acceptance Criteria

- A logged-in user can call any protected API endpoint with a valid JWT.
- An expired JWT is rejected with 401.
- A request without an Authorization header is rejected with 401.
- The JWKS endpoint is fetched and cached at startup; cache refresh happens automatically.

---

## Regression Risks

- Changing `SecurityConfig` matcher patterns can accidentally expose or block endpoints.
- Changes to JWKS caching can cause authentication failures under load.
