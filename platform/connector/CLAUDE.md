# Connector — Agent Guide

Java 21 / Spring Boot 3.x service, built with Gradle (the other platform modules use Maven). Provides the foundation for future connector integrations. See root `CLAUDE.md` for git/PR rules.

---

## Critical Rules

- **Session execution**: the connector polls `backend-api`'s session queue (`poll` command), claims the next queued session for its registered agent, prepares an isolated git workspace, and writes a `task.md` brief — see `session/`, `workspace/`, `git/`, and `context/` below. Actually *running* the agentic task inside the prepared workspace is still out of scope; `SessionProcessor` stops once the session is marked `RUNNING`.
- **Configuration** lives under `config/` (`@ConfigurationProperties`), split by concern: `ConnectorProperties` (`connector`), `SessionPollingProperties` (`connector.polling`), `WorkspaceProperties` (`connector.workspace`), `TaskContextProperties` (`connector.context`) — bind new settings there instead of reading `Environment`/env vars directly in business logic.

---

## Running Locally

```bash
./gradlew bootRun            # Connector at http://localhost:8081
./gradlew test                # Run unit tests
./gradlew bootJar             # Build fat JAR
docker build -t mesha-connector .   # Docker image (multi-stage)
```

Health check: `GET /actuator/health`

---

## Package Map

```
src/main/java/com/mesha/connector/
├── ConnectorApplication.java          # @SpringBootApplication entry point; runs web-less for `login`/`register`/`heartbeat`/`poll`
├── cli/
│   ├── LoginCommandRunner.java        # Handles `login --token=<token>` invocations
│   ├── RegisterCommandRunner.java     # Handles `register --executor-type=<type> [--capabilities=a,b,c]`
│   ├── HeartbeatCommandRunner.java    # Handles `heartbeat` invocations
│   └── PollCommandRunner.java         # Handles `poll` invocations; runs SessionPollingLoop for the registered agent
├── auth/
│   ├── ConnectorAuthService.java      # Login orchestration, exposes getValidAccessToken()
│   ├── ConnectorAuthClient.java       # HTTP calls to backend-api /api/connector/auth/*
│   ├── ConnectorAuthInterceptor.java  # Attaches Bearer token to outgoing backendApiRestClient calls
│   ├── ConnectorTokenStore.java       # Persists credentials to connector.credentials-path (chmod 600)
│   └── ConnectorCredentials.java      # accessToken + access token expiry
├── agent/
│   ├── AgentRegistrationService.java  # Register/heartbeat orchestration
│   ├── AgentRegistrationClient.java   # HTTP calls to backend-api /api/connector/agents/*
│   ├── AgentRegistrationStore.java    # Persists the registered agent id to connector.agent-registration-path (chmod 600)
│   └── AgentRegistration.java         # agentId/hostname/executorType persisted locally
├── session/
│   ├── ConnectorAgentSessionClient.java # HTTP calls to backend-api /api/connector/agent-sessions/*
│   ├── SessionPollingLoop.java        # Claims sessions in a loop with exponential backoff on failure
│   ├── SessionProcessor.java          # Prepares a claimed session: workspace, git branch, task.md, status updates
│   ├── ConnectorSessionStatus.java    # Status values a connector can report
│   ├── SessionPollingException.java   # Wraps claim/context/status-update HTTP failures
│   └── dto/                           # Wire DTOs mirroring backend-api's connector session DTOs
├── workspace/
│   ├── WorkspaceManager.java          # Creates/reuses/cleans up per-ticket workspace directories
│   ├── CleanupPolicy.java             # NEVER / ON_SUCCESS / ALWAYS
│   └── WorkspaceException.java
├── git/
│   ├── GitWorkspacePreparer.java      # Clones/updates a repo and checks out the working branch via the system git binary
│   ├── BranchNamingStrategy.java      # feature/<issue-identifier>
│   └── GitCommandException.java
├── context/
│   └── SessionContextBuilder.java     # Renders a claimed session's context into task.md
└── config/
    ├── ConnectorProperties.java       # connector.* configuration abstraction
    ├── SessionPollingProperties.java  # connector.polling.* (interval, backoff)
    ├── WorkspaceProperties.java       # connector.workspace.* (root, cleanup policy)
    ├── TaskContextProperties.java     # connector.context.* (max description length)
    └── BackendApiClientConfig.java    # backendApiRestClient bean for authenticated backend calls
```

Authentication: `mesha-connector login --token=<connector-access-token>` takes the `mcat_...` access
token copied from the "Connector Access Token" generator in the Mesha web app, validates it against
the backend via `GET /api/connector/auth/validate`, and stores it locally as-is — there is no token
exchange and no refresh token. The access token is long-lived (30 days); once it expires, re-run
`login` with a freshly generated token. Subsequent backend calls made through the
`backendApiRestClient` bean are authenticated automatically by `ConnectorAuthInterceptor`, which
attaches the stored token as a Bearer header.

Agent registration: `mesha-connector register --executor-type=<type> [--capabilities=a,b,c]` registers
this machine/executor combination as a Mesha agent via `POST /api/connector/agents/register`
(hostname auto-detected) and persists the returned agent id locally. `mesha-connector heartbeat`
sends a heartbeat for that agent via `POST /api/connector/agents/{agentId}/heartbeat`. Re-running
`register` for the same hostname/executor-type reconnects to the existing agent instead of creating
a duplicate.

Session polling: `mesha-connector poll` reads the locally persisted agent id and runs
`SessionPollingLoop` until the process is terminated. Each cycle atomically claims the next queued
session for that agent via `POST /api/connector/agent-sessions/claim` (backend uses
`SELECT ... FOR UPDATE SKIP LOCKED`, so concurrent connectors never double-claim), fetches the full
ticket context via `GET /api/connector/agent-sessions/{id}/context`, and hands it to
`SessionProcessor`, which:
1. Marks the session `PREPARING`.
2. Resolves a workspace directory keyed by issue identifier (`WorkspaceManager`, e.g.
   `~/mesha-workspaces/MES-123`) — retried/follow-up sessions for the same ticket reuse the clone.
3. Clones (or updates) the repository and checks out `feature/<issue-identifier>`
   (`GitWorkspacePreparer` + `BranchNamingStrategy`).
4. Writes `task.md` into the workspace (`SessionContextBuilder`): title, status/priority, the
   description split into sections, acceptance criteria, comments, and related tickets.
5. Marks the session `RUNNING` with the branch name and workspace path. Any failure in this
   pipeline is reported back as `FAILED` with the error message instead of left hanging.

Polling cadence and backoff are configured under `connector.polling.*`; workspace root and cleanup
policy under `connector.workspace.*`; `task.md` size limits under `connector.context.*` — see
`application.yml` for defaults and the matching `CONNECTOR_*` env var overrides.
