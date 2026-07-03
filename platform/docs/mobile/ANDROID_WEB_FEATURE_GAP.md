# Android vs. Web UI — Feature Gap Analysis

**Purpose:** Enumerate every user-facing project-management / ticket feature that exists in the **web frontend** (`platform/frontend`) but is **missing or incomplete in the Android app** (`platform/mobile`).

**Date:** 2026-07-02
**Web reference root:** `platform/frontend/src`
**Android reference root:** `platform/mobile/.../com/mesha/mobile`

> A recurring theme: the Android app's DTOs (`data/remote/dto/Dtos.kt`) already carry many of these fields (`assigneeId`, `agentType`, `agentLlm`, `labelIds`, `parentId`, `aiAssignmentState`), but no Compose screen or ViewModel reads or writes them. So many gaps are **UI-only gaps** — the data plumbing exists, the screens do not.

**Legend**
- ❌ **Missing** — no equivalent in Android at all
- ⚠️ **Partial** — present but read-only or significantly reduced vs. web
- ✅ **Done** — implemented on Android

---

## Implementation status (2026-07-03)

The following gaps are now **closed** in the Android app (`platform/mobile`). Each is
backed by an existing REST endpoint — no backend changes were required.

| Ref | Feature | Where |
|-----|---------|-------|
| 2.1–2.6 | Issue **search, status/priority filters, sort, load-more pagination** | `IssuesScreen.kt` / `IssuesViewModel.kt` |
| 3.1–3.3 | **Assign issue to a human** (member picker) and **to an AI agent** (assign/unassign) | `IssueDetailScreen.kt` / `IssueDetailViewModel.kt` |
| 4.1, 4.2, 4.4 | **Add/remove labels** on an issue, **create a label inline**, colored label chips | issue detail label editor |
| 5.1, 5.3 | **Per-project custom statuses** (from `/statuses`) with **status colors**, replacing the hardcoded enum | issue list + detail status picker |
| 6.1, 6.2 | **Manual (non-AI) create-issue form** with status / priority / assignee / label selection | `CreateIssueManualScreen.kt` |
| 7.1 | **Delete ticket** | issue detail overflow menu |
| 7.4 | **Activity feed / timeline** | issue detail |
| 7.5, 7.6 | **Comment threading (replies)** and **delete comment** | issue detail comments |
| 8.1, 8.2, 8.3, 8.4 | **Start / cancel a Blocks AI session from the issue**, execution-state badges, PR + CI status | issue detail AI Sessions panel |
| 11.2 | **PR badge with CI status** on list cards | `IssuesScreen.kt` |

### Second wave (2026-07-03) — board & governance

| Ref | Feature | Where |
|-----|---------|-------|
| 1.1, 1.2 | **Board / Kanban view** — status columns grouped from `/statuses`, per-card **tap-to-move** status action (optimistic, reverts on rule violation), **List/Board view switcher** in the top bar | `IssuesScreen.kt` (`BoardView`/`BoardColumn`/`BoardCard`) / `IssuesViewModel.kt` |
| 8.7 | **AI Agent definitions CRUD** — list / create / edit / delete custom agents (title, name, provider, Blocks agent name, system prompt, startup commands, active toggle) | `ui/screens/agentconfig/AgentConfigScreen.kt` + `AgentConfigViewModel.kt` |
| 9.1 | **Automation rules** — view / create / enable-disable / delete `trigger → action(s)` rules (status & label values picked from project statuses / workspace labels) | `ui/screens/rules/RulesScreen.kt` + `RulesViewModel.kt` |
| 9.2 | **Ticket rules / guardrails** — view / create / enable-disable / delete `conditions → restrictions` rules | `ui/screens/rules/RulesScreen.kt` + `RulesViewModel.kt` |
| 9.3 | **Rule-violation surfacing** on a blocked board move (rejected status change shows the server message and reverts the card) | `IssuesScreen.kt` move dialog / `IssuesViewModel.moveIssueStatus` |

The rules & agent-config screens are reachable from **Settings → Workspace**
("Custom AI agents", "Automations & ticket rules").

Still open (not in this change): true **drag-and-drop** board moves (Android uses
tap-to-move instead), attachments (7.3), issue links / sub-issues (7.2),
connector-agent / token CRUD (8.8), per-action **conditions** in the automation
editor (rules are created without conditions; existing conditions are shown when
listing), a dedicated rule-violation dialog on the **issue-detail** status change
(9.3, board moves are covered), GitHub/Blocks integration config (10.1–10.3), and
real-time SSE (11.1). These remain tracked in the tables below.

---

## 1. Views & Navigation

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 1.1 | **Board / Kanban view** — status columns, drag issue between columns (optimistic status update), drag to reorder columns, add-status column, per-column "add issue" | `components/issues/KanbanView.tsx`, `KanbanColumn.tsx`, `KanbanCard.tsx`, `AddStatusColumn.tsx`, `MoveStatusSheet.tsx` | ❌ Missing — only a flat list (`IssuesScreen.kt`) exists |
| 1.2 | **List / board view switcher** (persisted preference) | `components/issues/ViewSwitcher.tsx` | ❌ Missing |
| 1.3 | **Table columns** (Status, Priority, Labels, Assignee, Updated, PR columns) with inline-interactive cells | `components/issues/ListView.tsx` | ⚠️ Partial — rows show identifier, priority, title, status only; no inline edit, no assignee/labels/PR columns |

---

## 2. Filters, Sorting, Search

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 2.1 | **Search issues by title** | `components/issues/IssueFilters.tsx` | ❌ Missing |
| 2.2 | **Filter by status** | `IssueFilters.tsx` | ❌ Missing |
| 2.3 | **Filter by priority** | `IssueFilters.tsx` | ❌ Missing |
| 2.4 | **Filter by label** (multi-select) | `IssueFilters.tsx` | ❌ Missing |
| 2.5 | **Sort** by title / status / priority / updated (asc/desc) | `ListView.tsx` (`SortButton`) | ❌ Missing |
| 2.6 | **Pagination / load-more** | `components/ui/Pagination.tsx`, `hooks/useIssues.ts` | ❌ Missing — Android fetches only page 0 (size 50) |

---

## 3. Ticket Assignee

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 3.1 | **Assign issue to a human** (workspace member picker) | `components/issues/AssigneeSelector.tsx`, `hooks/useWorkspaceMembers.ts` | ❌ Missing — `assignee` is display-only text; `assigneeId` never set |
| 3.2 | **Unassign / "No assignee"** | `AssigneeSelector.tsx` | ❌ Missing |
| 3.3 | **Assign issue to an AI agent** (Blocks / connector) directly from the issue | `AssigneeSelector.tsx`, `hooks/useIssueAgents.ts` (`useAssignAgent`, `useUnassignAgent`) | ❌ Missing — `agentType`/`agentLlm`/`aiAssignmentState` exist in DTO but are never read/written |
| 3.4 | **Multi-agent assign/unassign panel** | `components/issues/AssignedAgentsPanel.tsx` | ❌ Missing |
| 3.5 | **Inline assignee edit from list/board cards** | `ListView.tsx`, `KanbanCard.tsx` | ❌ Missing |

---

## 4. Labels

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 4.1 | **Add / remove labels on an existing issue** | detail sidebar label editor, `components/issues/LabelSelector.tsx` | ❌ Missing — labels shown as read-only comma-joined text |
| 4.2 | **Create a new label inline** (name + color) | detail label editor, `hooks/useLabels.ts` | ❌ Missing |
| 4.3 | **Workspace label management** (list/create/delete) | `hooks/useLabels.ts` | ❌ Missing (only fetched internally for AI-draft sync) |
| 4.4 | **Label chips with color** | throughout web | ⚠️ Partial — plain text names, no color |

---

## 5. Statuses (workflow stages)

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 5.1 | **Per-project custom statuses** (not a fixed enum) | `hooks/useProjectStatuses.ts` | ❌ Missing — Android hardcodes `BACKLOG/TODO/IN_PROGRESS/REVIEW/DONE` in `IssueDetailScreen.kt` |
| 5.2 | **Create / edit / delete / reorder statuses** | `components/settings/StatusesSection.tsx`, `AddStatusColumn.tsx` | ❌ Missing |
| 5.3 | **Status colors** | web status badges | ❌ Missing |

---

## 6. Ticket Creation

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 6.1 | **Manual (non-AI) create-issue form** | `components/issues/CreateIssueModal.tsx` | ❌ Missing — Android only has the AI-draft flow |
| 6.2 | **Set assignee / label multi-select / status on creation** | `CreateIssueModal.tsx` | ⚠️ Partial — AI flow picks labels via model; no assignee, no manual label pick, no status pick |
| 6.3 | **Attach issue links during creation** | `CreateIssueModal.tsx` | ❌ Missing |
| 6.4 | **AI-generated draft** (prompt → review/edit → approve) | `components/issues/AIDraftModal.tsx` | ✅ Present (on-device Gemma; Android is arguably richer here — voice input, offline queue) |

---

## 7. Ticket Detail — Actions & Panels

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 7.1 | **Delete ticket** | detail Danger Zone, `useDeleteIssue` | ❌ Missing |
| 7.2 | **Issue links / sub-issues** (DEPENDS_ON, BLOCKS, DUPLICATE_OF, PARENT_OF/CHILD_OF) | `components/issues/IssueLinksPanel.tsx`, `hooks/useIssueLinks.ts` | ❌ Missing — no DTOs, no UI |
| 7.3 | **Attachments** (upload/download/delete, ≤10 MB) | `components/issues/IssueAttachmentsPanel.tsx` | ❌ Missing |
| 7.4 | **Activity feed / timeline** (status/priority/assignee/label/AI events) | `components/activity/ActivityFeed.tsx` | ❌ Missing |
| 7.5 | **Comment threading / replies** (`parentId`) | `components/comments/CommentThread.tsx` | ⚠️ Partial — `parentId` in DTO/repo, but UI is flat (no reply/nest) |
| 7.6 | **Delete comment** | `hooks/useComments.ts` | ❌ Missing |

---

## 8. AI Agents, Blocks Sessions & PR Review

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 8.1 | **Start a Blocks AI session on an issue** (with instructions) | `components/blocks/AISessionsPanel.tsx`, `hooks/useBlocksSessions.ts` | ❌ Missing — Android can only view sessions & send follow-ups |
| 8.2 | **Cancel an AI session** | `AISessionsPanel.tsx`, `useCancelBlocksSession` | ❌ Missing |
| 8.3 | **AI execution-state badges** (Planning/Coding/Waiting Review/PR Opened/…) | `AISessionsPanel.tsx` | ⚠️ Partial — Android shows a basic status badge only |
| 8.4 | **PR cards with CI checks status** (success/failure/pending) | `AISessionsPanel.tsx`, `components/blocks/ResourcesPanel.tsx` | ⚠️ Partial — Android shows PR title/number/URL as text; no CI status, no branch/resource dedup |
| 8.5 | **Resources panel** (dedup PRs/branches across sessions) | `components/blocks/ResourcesPanel.tsx` | ❌ Missing |
| 8.6 | **Assign to AI from issue → auto-create connector session + enqueue** | `AssigneeSelector.tsx`, `useCreateConnectorSession`, `useEnqueueAgentSession` | ❌ Missing |
| 8.7 | **AI Agent definitions CRUD** (title, name, system prompt, startup commands, active toggle) | `app/.../agents/page.tsx`, `hooks/useAgentDefinitions.ts` | ❌ Missing — Android agents list is read-only |
| 8.8 | **Connector agents / token management** (register/monitor self-hosted executors) | `app/.../connector-agents/page.tsx`, `components/connector/ConnectorTokenGenerator.tsx` | ❌ Missing |
| 8.9 | **Follow-up messaging to a session** | `useSendBlocksMessage` / `useSendAgentSessionMessage` | ✅ Present (`SessionDetailScreen.kt`) |

---

## 9. Automation & Governance (Settings)

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 9.1 | **Automation rules** (trigger → action: PR_OPENED/MERGED, STATUS_UPDATED, LABEL_ADDED → SET_STATUS / ADD_LABEL / START_AI_SESSION, with conditions) | `components/automation/AutomationRulesSection.tsx`, `hooks/useAutomations.ts` | ❌ Missing entirely |
| 9.2 | **Ticket Rules / guardrails** (conditions → CANNOT_START_AI_SESSION / CANNOT_MOVE_TO_STATUS) | `components/settings/TicketRulesSection.tsx`, `hooks/useTicketRules.ts` | ❌ Missing |
| 9.3 | **Rule-violation dialog** on blocked status change / AI start | `components/ui/RuleViolationDialog.tsx`, `lib/error-utils.ts` | ❌ Missing |

---

## 10. Integrations & Workspace

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 10.1 | **GitHub integration** (connect repo, view PRs) | `app/.../github/...` | ❌ Missing |
| 10.2 | **Blocks integration config** (connect/configure Blocks AI) | `app/.../blocks/page.tsx` | ❌ Missing |
| 10.3 | **Project settings** (statuses / automations / rules pages) | `app/.../settings/...` | ❌ Missing |
| 10.4 | **Workspace switcher** | Sidebar | ⚠️ Partial — Android auto-selects the first workspace only |
| 10.5 | **Project create / edit** | web projects page | ⚠️ Partial — Android projects list is read-only |

---

## 11. Cross-cutting / Real-time

| # | Feature | Web location | Android status |
|---|---------|--------------|----------------|
| 11.1 | **Real-time updates (SSE)** — live issue/activity refresh | `hooks/useIssueEvents.ts` (`/issues/stream`) | ❌ Missing — Android relies on manual refresh |
| 11.2 | **PR badges on list/board cards** (state color + CI status) | `LinkedPullRequest` across list/board | ❌ Missing on list |
| 11.3 | **Inline PR / CI check status icons** | web throughout | ❌ Missing |

---

## Summary — priority gaps

Ranked by likely user impact for closing the mobile experience gap:

1. **Board / Kanban view** with drag-to-move status (1.1, 1.2)
2. **Assignee management** — humans *and* AI agents from the issue (3.1–3.5)
3. **Label editing** on existing issues (4.1–4.3)
4. **Per-project custom statuses** instead of hardcoded enum (5.1–5.3)
5. **Start/cancel AI (Blocks) sessions from the issue**, richer PR/CI review (8.1–8.5)
6. **Filters, search, sort** on the issue list (2.1–2.5)
7. **Manual issue creation** form + assignee/status/label on create (6.1–6.3)
8. **Delete ticket**, **attachments**, **issue links/sub-issues**, **activity feed** (7.1–7.4)
9. **Automation rules & ticket rules** governance (9.1–9.3)
10. **Real-time SSE updates** (11.1)

### Areas where Android leads web
- On-device (offline) AI issue drafting with **voice dictation** and an **offline draft queue** (WorkManager retry) — no web equivalent.
- Local LLM chat.

### Features absent in *both* (not gaps, just noting)
Due dates, time/estimates, milestones/cycles, saved views, bulk actions, comment reactions/mentions.
