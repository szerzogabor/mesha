# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Git Branching Rules

**At the start of every coding session, create a fresh branch from the latest `main`:**

```bash
git fetch origin main
git checkout -b <branch-name> origin/main
```

### Branch Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Feature / new work | `feature/<ticket-id>` | `feature/TP-42` |
| Bug fix | `fix/<ticket-id>` | `fix/TP-39` |
| No ticket | `feature/<short-description>` | `feature/improve-error-messages` |

### Important Instructions

1. **NEVER** commit directly to `main` or reuse an existing branch from a prior session
2. **ALWAYS** branch from the latest `main` — never from another feature branch
3. **COMMIT** your work with clear, descriptive commit messages
4. **PUSH** your branch and open a pull request when complete
5. **NEVER** push to a branch you did not create in the current session without explicit permission

---

## Git Operations

Follow these practices for git:

**For git push:**
- Always use "szerzogabor@gmail.com" as a contributor for commits. This is the ONLY acceptable contributor. Remove any other contributor
- Always use git push -u origin <branch-name>
- Only if push fails due to network errors retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s)
- Example retry logic: try push, wait 2s if failed, try again, wait 4s if failed, try again, etc.
- IMPORTANT: After pushing your changes, ALWAYS create a pull request for the pushed branch if one does not already exist. Create the pull request as ready for review (not a draft). You do not need to ask the user first.

**For git fetch/pull:**
- Prefer fetching specific branches: git fetch origin <branch-name>
- If network failures occur, retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s)
- For pulls use: git pull origin <branch-name>

---

## Linear Ticket Workflow Rules

All AI agents (Claude Code, Codex, Gemini, etc.) working on this repository MUST follow these rules when working with Linear tickets:

1. **Start working only on tickets in "to do" status** — Do not begin work on tickets that have any other status
2. **Move ticket to "In Progress" when starting** — Immediately after beginning work on a ticket, update its status to "In Progress"
3. Use the `work-ticket` skill

These rules ensure proper ticket lifecycle management and prevent conflicts with other developers.

---

## Pull Request Workflow Rules

All AI agents (Claude Code, Codex, Gemini, etc.) working on this repository MUST follow these rules when creating pull requests:

1. Make sure ONLY "szerzogabor@gmail.com" is in the contributor list for every commit
2. **PR Title Format**: When a Linear ticket ID is provided in the session context (e.g., `BLO-123`), prefix the PR title with that identifier. Format: `BLO-123: <description>`. Example: `BLO-123: Add feature X`. If no ticket ID is available, use a descriptive title without a prefix.

---

## Worker Code Location — CRITICAL

**IMPORTANT:** The `platform/backend-worker` module is **NOT** the active worker implementation and must remain untouched.

All worker-related changes (session polling, Blocks adapter, scheduling, orchestration) must be made exclusively under:

```
platform/backend-api/src/main/java/com/mesha/api/worker/
```

The worker logic lives inside the `backend-api` Spring Boot service and uses the full JPA entity model directly. Never create separate projection classes or modify files under `platform/backend-worker/` for worker tasks.

---

## Project Overview

Mesha is an **AI-native project management platform** where users create tickets from natural language, assign them to AI agents (Blocks), and review the resulting GitHub Pull Requests. The system is human-in-the-loop — AI never merges or deploys without explicit approval.

**Detailed codebase reference:** [`platform/docs/CODEBASE.md`](platform/docs/CODEBASE.md)

---

## Repository Structure

```
mesha/
├── platform/
│   ├── backend-api/        # Active REST API + embedded worker (Java 21, Spring Boot)
│   ├── backend-worker/     # Reference only — DO NOT modify
│   ├── frontend/           # Next.js 15 web app (TypeScript)
│   ├── infrastructure/     # Docker Compose files
│   ├── docs/               # Architecture documentation
│   └── scripts/            # Dev convenience scripts
├── .github/workflows/      # CI/CD (GitHub Actions)
├── render.yaml             # Render IaC deployment config
└── CLAUDE.md               # This file
```

---

## Module-Specific Guides

For deeper module-specific context, read these before making changes:

| Module | Guide |
|--------|-------|
| Backend API | [`platform/backend-api/CLAUDE.md`](platform/backend-api/CLAUDE.md) |
| Frontend | [`platform/frontend/CLAUDE.md`](platform/frontend/CLAUDE.md) |
| Full codebase map | [`platform/docs/CODEBASE.md`](platform/docs/CODEBASE.md) |

---

## Key Architectural Constraints

1. **Worker code lives in `backend-api`** — not in `backend-worker` (which is a reference impl)
2. **Authentication via Clerk** — JWT tokens, all API endpoints require `Authorization: Bearer <token>` except webhooks
3. **Database migrations via Flyway** — never modify existing migration files; always add a new `V{n+1}__*.sql`
4. **AI provider abstraction** — use `ProviderAdapter` interface; don't call Blocks API directly from business logic
5. **Automation rules evaluate synchronously** — triggered from service layer on state changes
6. **Frontend calls backend REST API** — no direct DB access from frontend
7. **No auto-merge** — AI creates PRs; humans merge

---

## Quick Start

```bash
# 1. Start local infrastructure (PostgreSQL + Redis)
bash platform/scripts/start-dev.sh

# 2. Backend API (http://localhost:8080)
cd platform/backend-api && mvn spring-boot:run

# 3. Frontend (http://localhost:3000)
cd platform/frontend && npm install && npm run dev
```

---

## Tech Stack Summary

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 15, TypeScript, TailwindCSS, TanStack Query, Zustand, Clerk |
| Backend | Java 21, Spring Boot 3.3, Spring Data JPA, Spring Security |
| Database | PostgreSQL 16 + Flyway (32 migrations) |
| Cache | Redis (Upstash) |
| AI Provider | Blocks API (abstracted via ProviderAdapter) |
| GitHub | GitHub App (JWT auth, webhooks) |
| Auth | Clerk (OAuth2 / JWT) |
| Hosting | Vercel (frontend) + Render (backend + DB) |
| Observability | OpenTelemetry → Grafana Cloud (Tempo + Loki + Prometheus) |
