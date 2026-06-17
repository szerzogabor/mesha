# Agent Definitions

## Purpose

Allow workspace administrators to configure custom AI agents with specific names, system prompts, LLM preferences, and provider configurations — enabling teams to define specialized agents for different tasks (e.g., a backend-only agent, a documentation agent).

---

## Functional Requirements

1. `ADMIN` and `OWNER` members can create, update, and list agent definitions for a workspace.
2. Each agent definition has: name, title, system prompt, provider type (`BLOCKS`), and optional LLM preference (`claude`, `codex`).
3. Agent definitions are workspace-scoped and shared across all projects.
4. An agent definition can be assigned to an issue (creating an `IssueAgent` record).
5. Assigned agents are visible in the issue detail's "Assigned Agents" panel.

---

## Business Rules

- Agent definitions are workspace-scoped (not project-scoped).
- Currently only `BLOCKS` is a valid `provider_type`.
- LLM options: `claude`, `codex` (maps to specific models in the Blocks API).
- Multiple agents can be assigned to the same issue.
- Deleting an agent definition does not retroactively remove its assignments on issues.

---

## Validation Rules

- `name`: required, 1–100 characters, unique within workspace.
- `title`: optional, 1–200 characters.
- `systemPrompt`: optional, max 10,000 characters.
- `providerType`: required, must be `BLOCKS`.
- `llm`: optional, must be `claude` or `codex` if provided.

---

## Error Scenarios

| Scenario | Behavior |
|----------|----------|
| Duplicate agent name in workspace | `409 Conflict` |
| Non-admin creating agent | `403 Forbidden` |
| Invalid `providerType` | `400 Bad Request` |
| Invalid `llm` value | `400 Bad Request` |

---

## Dependencies

- `agent_definitions` table
- `issue_agents` table
- `AgentDefinitionService`, `AgentDefinitionController`
- `IssueAgentService`, `IssueAgentController`
- `AssignedAgentsPanel.tsx` (frontend)

---

## Acceptance Criteria

- A workspace admin can create an agent definition and see it available when assigning to issues.
- Two agents with the same name in the same workspace are rejected.
- An agent assigned to an issue appears in the "Assigned Agents" panel.
- Agent definitions list is scoped to the current workspace.

---

## Regression Risks

- Removing the workspace scope filter in `AgentDefinitionRepository` exposes agents from other workspaces.
- Changing `AgentProviderType` enum values without migration breaks existing records.
