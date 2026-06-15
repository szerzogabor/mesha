# Mesha — AI-Native Project Management Platform

Mesha is an AI-native project management platform where users create tickets from natural language, assign them to AI agents, and review the resulting Pull Requests — all from a mobile-first web interface.

---

## Architecture

Full architecture documentation lives in [`platform/docs/architecture/`](./platform/docs/architecture/).

| Document | Description |
|---|---|
| [Overview](./platform/docs/architecture/overview.md) | Vision, high-level architecture, core principles |
| [Backend](./platform/docs/architecture/backend.md) | Services, domain model, database schema, flows |
| [Frontend](./platform/docs/architecture/frontend.md) | Next.js stack, UX principles, mobile-first design |
| [AI Integration](./platform/docs/architecture/ai-integration.md) | Blocks adapter, provider abstraction, context engine |
| [GitHub Integration](./platform/docs/architecture/github-integration.md) | GitHub App, webhooks, PR sync |
| [Infrastructure](./platform/docs/architecture/infrastructure.md) | Hosting, databases, Redis, scaling strategy |
| [API Design](./platform/docs/architecture/api-design.md) | REST endpoints, WebSocket events, RBAC |
| [Security](./platform/docs/architecture/security.md) | Auth, permissions, audit logging |

---

## Repository Structure

```
platform/
 ├── frontend/          # Next.js 15 + TypeScript + TailwindCSS
 ├── backend-api/       # Spring Boot 3 REST API + WebSocket + embedded AI worker
 ├── shared/            # Shared types and utilities (future)
 ├── infrastructure/    # Docker Compose, infrastructure config
 ├── docs/
 │   └── architecture/  # Architecture documentation
 └── scripts/           # Developer convenience scripts
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 15, TypeScript, TailwindCSS, TanStack Query, Zustand |
| Backend | Java 21, Spring Boot 3, Spring Security |
| Database | PostgreSQL + Flyway migrations |
| Cache / Queue | Redis (Upstash) |
| Object Storage | Cloudflare R2 |
| AI | Blocks (provider-abstracted) |
| Frontend Hosting | Vercel |
| Backend Hosting | Render |

---

## Local Development

**Prerequisites:** Docker, Java 21, Node.js 22+, Maven

```bash
# Start infrastructure (PostgreSQL + Redis)
cd platform
bash scripts/start-dev.sh

# Backend API
cd platform/backend-api
mvn spring-boot:run

# Frontend
cd platform/frontend
cp .env.local.example .env.local
npm install
npm run dev
```

The frontend runs on `http://localhost:3000`, API on `http://localhost:8080`.

---

## Key Principles

- **Human-in-the-loop** — AI never merges or deploys without explicit approval
- **AI provider abstraction** — Blocks is a provider, not the core architecture
- **Event-driven** — all AI tasks are asynchronous
- **Mobile-first** — the same Next.js app works on desktop, tablet, and mobile

this is a test task
