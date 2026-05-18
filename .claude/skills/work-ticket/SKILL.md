---
name: work-ticket
description: Pick up and implement the next Linear ticket for the Mesha project. Use when the user says "work on the next ticket", "start a ticket", or similar.
---

# Work Ticket Skill

Execute a Linear ticket end-to-end: find the next Research ticket, implement it, open a PR, and monitor for CI/review activity.

## Silent Execution

**Do not narrate steps.** Do not print what you are doing as you do it (no "Finding tickets…", "Moving ticket to Agent Working…", "Creating branch…", etc.).

Only produce output for:
- A question that requires the user's decision before you can continue.
- An important status note once the work is done, e.g. "Implementation is done, PR #42 is open." or "There was a merge conflict but I resolved it — human review can continue."
- A blocker you cannot resolve on your own.

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

- Push the branch to origin.
- Open a Pull Request — title must include the ticket ID, e.g. `[MESH-42] Add user authentication`.
- In case of Backend changes name of the Pull Request has to contain the "[render preview]"

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
