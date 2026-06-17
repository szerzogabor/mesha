# Mesha Local Agent Architecture
## Qwen CLI Integration via Mesha Connector

Status: Proposed

Owner: Mesha

Version: 2.0

---

# Vision

Mesha should become an AI-native project management platform capable of delegating implementation work to local AI coding agents.

Target workflow:

```text
Issue
 ↓
Assign Agent
 ↓
Start Session
 ↓
Mesha Connector
 ↓
Qwen CLI
 ↓
Code Changes
 ↓
Pull Request
 ↓
Review
```

The Mesha Connector is responsible for orchestration.

The Qwen CLI is responsible for execution.

The Mesha Connector must treat Qwen CLI as a black-box execution engine.

The connector must not know:

- which model is being used
- whether Ollama is used
- whether a remote provider is used
- how inference is performed

These concerns belong exclusively to Qwen CLI.

This separation ensures that Mesha remains independent from model providers and AI runtimes.

---

# Architectural Principles

## Principle 1

Mesha is NOT a coding agent.

Mesha is an orchestration platform.

Responsibilities:

- Project management
- Issue management
- Session management
- Agent management
- Automation
- GitHub integration
- Reporting
- Observability

---

## Principle 2

Coding agents remain external.

Examples:

- Qwen CLI
- Claude Code
- Codex CLI
- Gemini CLI
- Future agents

---

## Principle 3

The Mesha Connector is the integration layer.

```text
Mesha
    ⇄
Mesha Connector
    ⇄
Coding Agent
```

The connector translates Mesha concepts into agent actions.

---

# Repository Structure

The connector should live inside the Mesha monorepo.

```text
platform/

├── frontend/
├── backend-api/
├── connector/
├── shared/
└── docs/
```

The connector is a first-class Mesha platform component.

It should be versioned, built, tested and released together with the rest of the platform.

---

# High-Level Architecture

```text
┌─────────────────────────┐
│        Mesha UI         │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│       Backend API       │
│                         │
│ Issues                  │
│ Sessions                │
│ Agents                  │
│ GitHub                  │
│ Automations             │
└────────────┬────────────┘
             │ HTTPS
             │
             ▼
┌─────────────────────────┐
│    Mesha Connector      │
│                         │
│ Agent Registry          │
│ Heartbeat Service       │
│ Task Poller             │
│ Session Manager         │
│ Workspace Manager       │
│ Git Manager             │
│ Event Streamer          │
│ Qwen Adapter            │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│       Qwen CLI          │
│                         │
│ (Black Box Executor)    │
└─────────────────────────┘
```

---

# Connector Philosophy

The connector is not an AI agent.

The connector is an orchestration runtime.

Responsibilities:

- Agent registration
- Heartbeats
- Session lifecycle
- Task claiming
- Workspace creation
- Git operations
- PR reporting
- Log streaming

Non-responsibilities:

- Planning
- Reasoning
- Tool selection
- Context management
- Model routing
- Inference

Those responsibilities belong to Qwen CLI.

This separation allows Mesha to support future execution engines without architectural changes.

---

# Mesha Agent Model

An agent represents a real execution endpoint.

Example:

```text
Backend Agent
```

Metadata:

```json
{
  "id": "backend-agent",
  "name": "Backend Agent",
  "executor": "qwen-cli",
  "host": "Gabor-PC",
  "status": "ONLINE"
}
```

---

# Connector Responsibilities

The connector owns:

- Agent registration
- Heartbeats
- Session claiming
- Session execution
- Workspace creation
- Git operations
- Log streaming
- Pull request reporting

The connector does NOT own:

- Issue management
- Session management
- Automation management
- Project management
- AI reasoning
- Model execution

Those remain in Mesha or Qwen CLI.

---

# Agent Registration

First startup:

```bash
mesha-connector login
```

The connector receives:

```json
{
  "workspaceId": "...",
  "agentId": "...",
  "token": "..."
}
```

The connector persists credentials locally.

---

# Heartbeat System

Every 30 seconds:

```http
POST /api/agents/heartbeat
```

Payload:

```json
{
  "agentId": "...",
  "status": "ONLINE",
  "executor": "qwen-cli"
}
```

---

# Session Lifecycle

State machine:

```text
CREATED
QUEUED
CLAIMED
PREPARING
RUNNING
WAITING_FOR_USER
COMPLETED
FAILED
CANCELLED
```

---

# Session Claiming

User:

```text
Assign Backend Agent
Start Session
```

Backend:

```text
Session -> QUEUED
```

Connector:

```http
GET /api/agents/tasks
```

Response:

```json
{
  "sessionId": "...",
  "issueId": "...",
  "title": "...",
  "description": "..."
}
```

Connector claims task.

State:

```text
CLAIMED
```

---

# Workspace Management

Each session gets its own isolated workspace.

Example:

```text
~/mesha-workspaces/MES-123/
```

Workflow:

```bash
git clone
git checkout -b mes-123
```

---

# Context Builder

The connector generates a task package.

Sources:

- Issue title
- Issue description
- Acceptance criteria
- Comments
- Related issues
- Repository documentation
- CLAUDE.md
- Architecture documents

Output:

```text
task.md
```

The connector builds context.

The connector does not decide how the context is interpreted.

That responsibility belongs to Qwen CLI.

---

# Qwen CLI Integration

The connector does not integrate with models.

The connector integrates with Qwen CLI.

Responsibilities:

```text
Connector
    ⇄
Qwen CLI
```

The connector launches Qwen CLI as a child process.

Example:

```bash
qwen task.md
```

or any future execution mechanism supported by Qwen CLI.

The connector must not:

- call Ollama directly
- call model APIs directly
- manage inference
- perform reasoning
- manage model routing

Those concerns belong to Qwen CLI.

Qwen CLI remains the execution engine.

The connector remains the orchestration layer.

---

# Execution Monitoring

The connector captures:

- stdout
- stderr
- exit code

Example:

```text
Reading repository...
Running tests...
Fixing compilation errors...
Creating branch...
```

These events are streamed back to Mesha.

---

# Session Logs

Backend endpoint:

```http
POST /api/sessions/{id}/logs
```

Example:

```json
{
  "level": "INFO",
  "message": "Running integration tests"
}
```

---

# Follow-Up Messages

User:

```text
Please add integration tests.
```

Backend stores message.

Connector receives:

```json
{
  "sessionId": "...",
  "message": "Please add integration tests."
}
```

The connector forwards the message to the active Qwen CLI session.

---

# Git Integration

Connector responsibilities:

```bash
git status
git add
git commit
git push
```

The connector owns Git orchestration.

Qwen owns implementation.

---

# Pull Request Reporting

After push:

```bash
gh pr create
```

Connector reports:

```json
{
  "sessionId": "...",
  "prUrl": "..."
}
```

Backend links:

```text
Issue
 ↓
Session
 ↓
PR
```

---

# Security Model

Connector token:

```text
agent_token
```

Permissions:

- scoped to workspace
- scoped to agent
- revocable

The connector never receives administrator permissions.

---

# Future Executors

Version 1 targets:

```text
Qwen CLI
```

Future versions may support:

```text
Claude Code
Codex CLI
Gemini CLI
Aider
OpenHands
```

The connector architecture must allow swapping execution engines through adapters.

Example:

```java
public interface ExecutorAdapter {

    void execute(Task task);

    void sendMessage(
        String sessionId,
        String message
    );

    void cancel(
        String sessionId
    );
}
```

Qwen CLI will be the first implementation.

The Mesha backend must never depend on executor-specific behavior.

---

# MVP Scope

Version 1 includes:

- Agent registration
- Heartbeats
- Session claiming
- Qwen CLI execution
- Git workflow
- Pull request creation
- Session logs
- Follow-up messages

Version 1 excludes:

- Model management
- Ollama integration
- Provider selection
- Claude Code
- Codex CLI
- Gemini CLI
- Multi-agent execution
- Distributed execution

The connector communicates only with Qwen CLI.

---

# Success Criteria

A user can:

1. Register a local agent
2. See the agent online in Mesha
3. Assign the agent to an issue
4. Start a session
5. Execute implementation through Qwen CLI
6. Observe logs in Mesha
7. Create a GitHub PR
8. Continue the session with follow-up instructions

Result:

```text
Issue
 ↓
Session
 ↓
Qwen CLI
 ↓
Code Changes
 ↓
Pull Request
 ↓
Review
```

while Mesha remains the orchestration layer and the connector remains the execution bridge.
