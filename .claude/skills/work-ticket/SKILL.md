---
name: work-ticket
description: Pick up and implement the next Jira ticket for the Mesh project. Use when the user says "work on the next ticket", "start a ticket", or similar.
---

# Work Ticket Skill

Execute a Jira ticket end-to-end: find the next Research ticket, implement it, open a PR, and monitor for CI/review activity.

## Silent Execution

**Do not narrate steps.** Do not print what you are doing as you do it (no "Finding tickets…", "Moving ticket to Agent Working…", "Creating branch…", etc.).

Only produce output for:
- A question that requires the user's decision before you can continue.
- An important status note once the work is done, e.g. "Implementation is done, PR #42 is open." or "There was a merge conflict but I resolved it — human review can continue."
- A blocker you cannot resolve on your own.

## Jira Access

Probe for the Atlassian Rovo MCP connector first:

```
ToolSearch("atlassianUserInfo", max_results=5)
```

If found, extract the tool name prefix (e.g. `mcp__a1b2c3d4__atlassianUserInfo` → prefix is `mcp__a1b2c3d4__`), then load the required schemas by substituting the actual UUID:

```
ToolSearch("select:mcp__<actual-uuid>__getAccessibleAtlassianResources,mcp__<actual-uuid>__searchJiraIssuesUsingJql,mcp__<actual-uuid>__getTransitionsForJiraIssue,mcp__<actual-uuid>__transitionJiraIssue")
```

Never use the literal string `<actual-uuid>` — replace it with the UUID discovered in the previous step.

Resolve `cloudId` at runtime via `getAccessibleAtlassianResources` — never hardcode it.

If the Rovo connector is not available, fall back to `scripts/jira.sh` (requires `JIRA_API_TOKEN` in the environment). See `jira/jira-mcp-aggregated-analysis.md` for full details on why this fallback exists and what NOT to do (don't loop ToolSearch, don't tell the user to re-authorize, don't add a project-level `.mcp.json` for Atlassian).

## Ticket Selection

Query for tickets in **Research** status, ordered by creation date ascending:

```jql
project = MESH AND status = "Research" ORDER BY created ASC
```

Pick the oldest one. Do not start work on tickets in any other status.

## Workflow

### 1. Start

- Move the ticket to **Agent Working** status before touching any code.
- Create a new feature branch: `feature/MESH-XX-short-description`. Never reuse an existing branch for a different ticket.

### 2. Implement

- Make all changes on the feature branch.
- Commit frequently with messages that explain *why*, not just what.
- Use TDD for implementing a ticket 
- use this to install vercel tool when working on frontend : "npx plugins add vercel/vercel-plugin" 

### 3. Finish

- For commits and opening PR's use only the "szerzogabor@gmail.com" so Gemini review automatically and vercel deploys in preview environment 
- Push the branch to origin.
- Open a Pull Request — title must include the ticket ID, e.g. `[MESH-42] Add user authentication`.
- Move the Jira ticket to **Human Review** status.

### Summary Table

| Step | Action |
|---|---|
| Before starting | Ticket must be in **Research** status |
| When starting | Move ticket → **Agent Working**, create `feature/MESH-XX-...` branch |
| During work | Commit with meaningful messages |
| After finishing | Push branch, open PR, move ticket → **Human Review** |

## PR Monitoring

After opening a PR, call `mcp__github__subscribe_pr_activity` with the repository owner, repository name, and pull request number so the session receives CI failures and review comments.

When an event arrives:
- **Clear, small fix** — make it, commit, push. No need to ask first.
- **Ambiguous or architecturally significant** — use `AskUserQuestion` to clarify before acting.
- **Duplicate or informational** — skip silently.

## Review Comment Workflow

When acting on review comments:
1. **Spawn a subagent** to implement the fixes — never inline in the main session.
2. **Reply to each addressed comment** using `mcp__github__add_reply_to_pull_request_comment` — at minimum "Fixed." Do this inside the subagent.
