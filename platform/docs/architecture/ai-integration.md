# AI Integration Architecture

## 1. Provider Abstraction

Blocks must not be directly coupled to business logic. The platform supports future providers:

```
AIOrchestrator
    ↓
ProviderAdapter Interface
    ↓
BlocksAdapter | OpenAIAdapter | ClaudeAdapter
```

The `AIOrchestrator` in `backend-worker` calls `ProviderAdapter`, which is implemented by `BlocksAdapter` for current use and can be swapped without changing business logic.

---

## 2. Blocks Adapter

**Responsibilities:**
- Session creation
- Message synchronization
- Session continuation
- Polling `final_message`
- Error handling and retry logic

---

## 3. Session Strategy

A single issue may contain multiple AI threads:

```
Issue
 ├── Planning Thread
 ├── Implementation Thread
 ├── Debugging Thread
 └── Review Thread
```

### Session Persistence

Persist per session:
- provider session ID
- status
- timestamps
- retry state
- related issue ID

---

## 4. AI Permissions

AI providers should never receive unrestricted permissions. Scoped capabilities:

```
READ_ISSUES
WRITE_COMMENTS
CREATE_BRANCH
CREATE_PR
READ_REPOSITORY
```

### Never Allow
- AI auto-merge to production
- Full organization admin permissions
- Frontend direct access to AI provider APIs

---

## 5. Context Engine

**Purpose:** Improve AI output quality via contextual loading.

### Context Sources

**Ticket Context:**
- issue title, comments, linked issues, labels, status history

**Repository Context:**
- README, architecture docs, ADRs, deployment docs, coding standards

**Runtime Context (future):**
- logs, metrics, traces, incidents

### Embedding Strategy

Store embeddings for:
- ticket descriptions
- repository docs
- architecture docs
- PR summaries

**Recommended:** pgvector extension on PostgreSQL.

---

## 6. Future: Multi-Agent Workflows

```
Planner Agent
    ↓
Implementation Agent
    ↓
Review Agent
    ↓
Security Agent
```
