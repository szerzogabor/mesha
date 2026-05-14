---
name: jira
description: Establish a Jira connection for the Mesh project. Use when you need to read or update Jira issues without working on a full ticket — e.g. checking status, querying issues, or updating fields.
---

# Jira Access Skill

Establish a working Jira connection. After this skill completes you have live Jira tools ready to call.

## Step 1 — Probe for the Rovo MCP Connector

```
ToolSearch("atlassianUserInfo", max_results=5)
```

If a result is returned, the Rovo connector is available. Note the full tool name (e.g. `mcp__a1b2c3d4-5e6f-7890-abcd-ef1234567890__atlassianUserInfo`) and extract the full UUID string between the first and last `__` (e.g. `a1b2c3d4-5e6f-7890-abcd-ef1234567890`). Do not truncate it at the first dash.

## Step 2 — Load Required Schemas

Substitute the actual UUID from Step 1 (never use the placeholder literally):

```
ToolSearch("select:mcp__<actual-uuid>__getAccessibleAtlassianResources,mcp__<actual-uuid>__searchJiraIssuesUsingJql,mcp__<actual-uuid>__getJiraIssue,mcp__<actual-uuid>__getTransitionsForJiraIssue,mcp__<actual-uuid>__transitionJiraIssue,mcp__<actual-uuid>__editJiraIssue,mcp__<actual-uuid>__addCommentToJiraIssue")
```

## Step 3 — Resolve Cloud ID

Call `getAccessibleAtlassianResources` and read the `id` field from the response. Use this value as `cloudId` in every subsequent Jira call. Never hardcode it.

## REST Fallback

If the Rovo connector is not available (Step 1 returns no results), fall back to `scripts/jira.sh`. This requires `JIRA_API_TOKEN` set in the environment.

Do NOT:
- Loop on `ToolSearch` hoping the connector appears
- Tell the user to re-authorize the connector
- Add a project-level `.mcp.json` for Atlassian (see `jira/jira-mcp-aggregated-analysis.md` for why this breaks Code Web)
