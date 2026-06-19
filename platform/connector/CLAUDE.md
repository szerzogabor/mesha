# Connector — Agent Guide

Java 21 / Spring Boot 3.x service, built with Gradle (the other platform modules use Maven). Provides the foundation for future connector integrations. See root `CLAUDE.md` for git/PR rules.

---

## Critical Rules

- **Out of scope for now**: Qwen execution (session claiming/running is handled by `backend-api`'s session queue; this connector only registers itself and sends heartbeats). Do not add execution logic without an explicit ticket.
- **Configuration** lives in `config/ConnectorProperties` (`@ConfigurationProperties(prefix = "connector")`) — bind new settings there instead of reading `Environment`/env vars directly in business logic.

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
├── ConnectorApplication.java          # @SpringBootApplication entry point; runs web-less for `login`/`register`/`heartbeat`
├── cli/
│   ├── LoginCommandRunner.java        # Handles `login --token=<token>` invocations
│   ├── RegisterCommandRunner.java     # Handles `register --executor-type=<type> [--capabilities=a,b,c]`
│   └── HeartbeatCommandRunner.java    # Handles `heartbeat` invocations
├── auth/
│   ├── ConnectorAuthService.java      # Login/refresh orchestration, exposes getValidAccessToken()
│   ├── ConnectorAuthClient.java       # HTTP calls to backend-api /api/connector/auth/*
│   ├── ConnectorAuthInterceptor.java  # Attaches Bearer token to outgoing backendApiRestClient calls
│   ├── ConnectorTokenStore.java       # Persists credentials to connector.credentials-path (chmod 600)
│   └── ConnectorCredentials.java      # accessToken/refreshToken + access token expiry
├── agent/
│   ├── AgentRegistrationService.java  # Register/heartbeat orchestration
│   ├── AgentRegistrationClient.java   # HTTP calls to backend-api /api/connector/agents/*
│   ├── AgentRegistrationStore.java    # Persists the registered agent id to connector.agent-registration-path (chmod 600)
│   └── AgentRegistration.java         # agentId/hostname/executorType persisted locally
└── config/
    ├── ConnectorProperties.java       # connector.* configuration abstraction
    └── BackendApiClientConfig.java    # backendApiRestClient bean for authenticated backend calls
```

Authentication: `mesha-connector login --token=<mesha-access-token>` exchanges a token the user
already obtained from Mesha for connector-specific credentials via `POST /api/connector/auth/login`,
then stores them locally. Subsequent backend calls made through the `backendApiRestClient` bean
are authenticated automatically by `ConnectorAuthInterceptor`, which refreshes the access token
via `POST /api/connector/auth/refresh` when it's close to expiry.

Agent registration: `mesha-connector register --executor-type=<type> [--capabilities=a,b,c]` registers
this machine/executor combination as a Mesha agent via `POST /api/connector/agents/register`
(hostname auto-detected) and persists the returned agent id locally. `mesha-connector heartbeat`
sends a heartbeat for that agent via `POST /api/connector/agents/{agentId}/heartbeat`. Re-running
`register` for the same hostname/executor-type reconnects to the existing agent instead of creating
a duplicate.
