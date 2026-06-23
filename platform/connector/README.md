# Mesha Connector

The Connector is a Java 21 / Spring Boot service that runs on a developer's machine
(or any executor host) and bridges it to the Mesha backend. It authenticates as a
local agent, registers itself as an executor, and polls `backend-api` for queued
agent sessions. For each claimed session it prepares an isolated git workspace
(clone/checkout the working branch) and writes a `task.md` brief describing the
ticket, ready to be picked up by an agentic coding tool.

Actually running the agentic task inside the prepared workspace is still out of
scope — the connector stops once the session is marked `RUNNING`.

Unlike the other backend services in this repo (which use Maven), the connector
is built with Gradle.

## Prerequisites

- Java 21
- A running `backend-api` instance (defaults to `http://localhost:8080`)
- `git` available on the `PATH` (used to clone/update workspaces)

## Running Locally

```bash
./gradlew bootRun            # Connector at http://localhost:8081
```

Health check: `GET /actuator/health`

### CLI commands

The same Spring Boot application also runs web-less for one-off CLI invocations:

```bash
# 1. Log in with a Mesha access token (exchanges it for connector credentials)
./gradlew bootRun --args='login --token=<mesha-access-token>'

# 2. Register this machine/executor as a Mesha agent
./gradlew bootRun --args='register --executor-type=<type> [--capabilities=a,b,c]'

# 3. Send a heartbeat for the registered agent
./gradlew bootRun --args='heartbeat'

# 4. Start polling for queued sessions (runs until terminated)
./gradlew bootRun --args='poll'
```

Credentials are stored at `~/.mesha/connector/credentials.json` and the
registered agent id at `~/.mesha/connector/agent.json` (both `chmod 600`).

## Building

```bash
./gradlew test                # Run unit tests
./gradlew bootJar             # Build fat JAR
docker build -t mesha-connector .   # Build Docker image (multi-stage)
```

## Configuration

All settings are under the `connector.*` prefix in
`src/main/resources/application.yml` and can be overridden via environment
variables:

| Env var | Default | Purpose |
|---|---|---|
| `CONNECTOR_BACKEND_URL` | `http://localhost:8080` | `backend-api` base URL |
| `CONNECTOR_CREDENTIALS_PATH` | `~/.mesha/connector/credentials.json` | Where login credentials are persisted |
| `CONNECTOR_AGENT_REGISTRATION_PATH` | `~/.mesha/connector/agent.json` | Where the registered agent id is persisted |
| `CONNECTOR_AUTO_CONNECT_ENABLED` | `false` | Auto-run login/register/poll on startup |
| `CONNECTOR_AUTO_CONNECT_TOKEN` | _(empty)_ | Mesha access token used for auto-connect |
| `CONNECTOR_AUTO_CONNECT_EXECUTOR_TYPE` | _(empty)_ | Executor type used for auto-connect |
| `CONNECTOR_AUTO_CONNECT_CAPABILITIES` | _(empty)_ | Comma-separated capabilities for auto-connect |
| `CONNECTOR_AUTO_CONNECT_HOSTNAME` | _(empty)_ | Hostname override for auto-connect |
| `CONNECTOR_POLLING_INTERVAL_MS` | `5000` | Delay between successful poll cycles |
| `CONNECTOR_POLLING_BACKOFF_BASE_MS` / `_MAX_MS` / `_MULTIPLIER` | `5000` / `60000` / `2.0` | Exponential backoff on poll failures |
| `CONNECTOR_WORKSPACE_ROOT` | `~/mesha-workspaces` | Root directory for per-ticket git workspaces |
| `CONNECTOR_WORKSPACE_CLEANUP_POLICY` | `NEVER` | `NEVER` / `ON_SUCCESS` / `ALWAYS` |
| `CONNECTOR_CONTEXT_MAX_DESCRIPTION_CHARS` | `20000` | Max ticket description length written into `task.md` |
| `PORT` | `8081` | HTTP port |

See [`CLAUDE.md`](./CLAUDE.md) for the package map and session-processing pipeline details.
