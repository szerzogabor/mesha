# User Registration

## Purpose

Allow new users to create an account in Mesha so they can create or join workspaces and manage projects.

---

## Functional Requirements

1. Users register via Clerk's hosted sign-up flow (email/password or OAuth providers).
2. On first sign-in after registration, the frontend calls `POST /api/auth/sync` to sync the Clerk user into the local `users` table.
3. The local user record stores: `clerk_user_id`, `email`, `name`.
4. If a user with the same `clerk_user_id` already exists, the record is updated (upsert).

---

## Business Rules

- User identity is managed entirely by Clerk. Mesha never stores passwords.
- `clerk_user_id` is the system-wide unique identifier for a user.
- A user must call `POST /api/auth/sync` before any other API call that references their user identity (e.g., creating a workspace, being added as a member).
- Users cannot self-assign workspace roles — they must be invited by an existing workspace member.

---

## Validation Rules

- `clerk_user_id`: required, unique in `users` table, provided by Clerk JWT.
- `email`: required, sourced from Clerk JWT claims.
- `name`: optional, sourced from Clerk JWT claims (`given_name` + `family_name`).

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Invalid or expired JWT | `401 Unauthorized` |
| `clerk_user_id` already exists | Upsert (update existing record) |
| Missing `email` in JWT claims | `400 Bad Request` |

---

## Dependencies

- Clerk (identity provider)
- `users` table
- `AuthController`, `UserService`

---

## Acceptance Criteria

- A new user can register via Clerk sign-up and subsequently call `POST /api/auth/sync` successfully.
- Calling `/api/auth/sync` twice with the same JWT does not create duplicate records.
- A user without a valid JWT cannot call any protected API endpoint.

---

## Regression Risks

- Changing JWT claim field names breaks `UserService.syncFromJwt()`.
- Removing the upsert logic causes `409 Conflict` for returning users.
