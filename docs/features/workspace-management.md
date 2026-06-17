# Workspace Management

## Purpose

A workspace is the top-level organizational unit in Mesha. Users create workspaces, invite members with specific roles, and organize work into projects within a workspace.

---

## Functional Requirements

1. Authenticated users can create a new workspace (they become the `OWNER`).
2. Users can list all workspaces they are members of.
3. Workspace owners can invite other users by email to join with a specific role.
4. Workspace members can be assigned roles: `OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER`.
5. Workspace owners can change member roles or remove members.
6. A workspace has a unique `slug` (URL-safe name).

---

## Business Rules

- The creator of a workspace automatically becomes the `OWNER`.
- A workspace must always have at least one `OWNER`.
- Only `OWNER` or `ADMIN` can invite members.
- Only `OWNER` can delete the workspace or transfer ownership.
- A user can belong to multiple workspaces with different roles in each.
- Workspace-scoped resources (labels, agent definitions, Blocks config, GitHub installations) are shared across all projects in the workspace.

---

## Validation Rules

- `name`: required, 1–100 characters.
- `slug`: required, unique, alphanumeric + hyphens, auto-generated from name if not provided.
- Role: must be one of `OWNER`, `ADMIN`, `DEVELOPER`, `VIEWER`.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Duplicate `slug` | `409 Conflict` |
| Non-member accessing workspace | `403 Forbidden` |
| Non-owner trying to delete workspace | `403 Forbidden` |
| Removing the last owner | `400 Bad Request` |

---

## Dependencies

- `workspaces` table
- `workspace_members` table
- `users` table
- `WorkspaceService`, `WorkspaceController`

---

## Acceptance Criteria

- A user can create a workspace and immediately see themselves as `OWNER` in the member list.
- A `VIEWER` cannot create projects or invite members.
- Deleting a workspace removes all associated projects, issues, and members.
- A workspace slug is unique and does not change after creation.

---

## Regression Risks

- Removing the role check in `WorkspaceService` exposes admin operations to `VIEWER` roles.
- Changing the slug generation algorithm can break existing workspace URLs.
