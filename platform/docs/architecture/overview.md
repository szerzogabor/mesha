# AI-Native Project Management Platform — System Overview

**Version:** 1.0
**Status:** Draft
**Target Stack:** Next.js + Vercel | Java 21 + Spring Boot + Render | Blocks AI | PostgreSQL | Redis

---

## 1. Vision

The platform is an AI-native project management system where users can:

- Create tickets from natural language prompts
- Review and approve AI-generated tickets
- Assign tickets to AI agents (Blocks)
- Let AI agents automatically work on implementation tasks
- Automatically generate Pull Requests on GitHub
- Review AI-generated PRs directly inside the application
- Track AI execution sessions and workflows
- Use the entire system from both desktop and mobile devices

Design principles:

- Human-in-the-loop workflows — AI never merges or deploys without explicit approval
- Event-driven backend processing — all AI tasks are asynchronous
- AI provider abstraction — Blocks is a provider, not the core
- Mobile-first UX
- Long-running asynchronous orchestration
- Future multi-agent extensibility

---

## 2. High-Level System Architecture

```
┌─────────────────────────────────────┐
│            Web / Mobile UI          │
│-------------------------------------│
│ Next.js Application                 │
│ - Ticket Management                 │
│ - AI Draft Review                   │
│ - PR Review                         │
│ - Session Monitoring                │
│ - Notifications                     │
└────────────────┬────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────┐
│               Vercel                │
│ Frontend Hosting / Edge Caching     │
└────────────────┬────────────────────┘
                 │ HTTPS / WebSocket
                 ▼
┌─────────────────────────────────────┐
│          API Gateway Layer          │
│ Auth / Rate Limiting / Routing      │
└────────────────┬────────────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────────┐
│                    Backend Platform                    │
│                                                        │
│  API Service (backend-api)                             │
│  - REST API + WebSocket                                │
│  - Ticket CRUD, PR APIs, AI Draft APIs                 │
│                                                        │
│  AI Worker Service (backend-worker)                    │
│  - Blocks orchestration                                │
│  - GitHub webhook processing                           │
│  - Session polling + retry                             │
│  - Context loading + embeddings                        │
│                                                        │
└───────────────┬─────────────────────┬──────────────────┘
                │                     │
                ▼                     ▼
      ┌─────────────────┐   ┌──────────────────┐
      │   PostgreSQL    │   │  Redis / Queue   │
      └─────────────────┘   └──────────────────┘
                │
                ▼
      ┌─────────────────┐
      │  Cloudflare R2  │
      │ Object Storage  │
      └─────────────────┘
                │
                ▼
┌─────────────────────────────────────┐
│            External Systems         │
│ Blocks | GitHub | OpenAI (future)   │
└─────────────────────────────────────┘
```

---

## 3. Core Architectural Principles

### 3.1 Human-in-the-loop

AI never deploys or merges code automatically without explicit human approval. Users always:
- Review generated tickets
- Approve AI execution
- Review Pull Requests
- Control merges

### 3.2 AI Provider Abstraction

Blocks must not be directly coupled to business logic. The platform supports future providers via:

```
AIOrchestrator
    ↓
ProviderAdapter Interface
    ↓
BlocksAdapter | OpenAIAdapter | ClaudeAdapter
```

### 3.3 Event-Driven Architecture

All long-running AI tasks are asynchronous:
- AI ticket generation
- Repository indexing
- PR generation
- Session synchronization
- Webhook handling

### 3.4 Mobile-First UX

The same Next.js application supports desktop, tablet, and mobile browsers. Future optional support: PWA, React Native wrapper.

---

## 4. Recommended Development Order

| Phase | Focus |
|---|---|
| 1 | Authentication, workspace/project model, ticket CRUD, responsive UI |
| 2 | AI ticket draft generation, draft review flow, approval flow |
| 3 | GitHub App integration, repository linking, PR synchronization |
| 4 | Blocks orchestration, session tracking, AI execution lifecycle |
| 5 | Context engine, embeddings, advanced AI workflows |

---

## 5. MVP Scope

### Include
- AI ticket draft generation
- Human approval flow
- GitHub integration
- Blocks assignment
- PR synchronization
- Mobile-responsive UI
- Session tracking

### Exclude Initially
- Multi-agent orchestration
- Autonomous deployment
- Complex CI integrations
- Runtime debugging agents
- Full IDE replacement
