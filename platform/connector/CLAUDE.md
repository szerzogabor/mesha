# Connector — Agent Guide

Java 21 / Spring Boot 3.x service, built with Gradle (the other platform modules use Maven). Provides the foundation for future connector integrations. See root `CLAUDE.md` for git/PR rules.

---

## Critical Rules

- **Out of scope for now**: agent registration, sessions, Qwen integration. Do not add these without an explicit ticket.
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
├── ConnectorApplication.java          # @SpringBootApplication entry point; runs web-less for `login`
├── cli/
│   └── LoginCommandRunner.java        # Handles `login --token=<token>` invocations
├── auth/
│   ├── ConnectorAuthService.java      # Login/refresh orchestration, exposes getValidAccessToken()
│   ├── ConnectorAuthClient.java       # HTTP calls to backend-api /api/connector/auth/*
│   ├── ConnectorAuthInterceptor.java  # Attaches Bearer token to outgoing backendApiRestClient calls
│   ├── ConnectorTokenStore.java       # Persists credentials to connector.credentials-path (chmod 600)
│   └── ConnectorCredentials.java      # accessToken/refreshToken + access token expiry
└── config/
    ├── ConnectorProperties.java       # connector.* configuration abstraction
    └── BackendApiClientConfig.java    # backendApiRestClient bean for future API calls
```

Authentication: `mesha-connector login --token=<mesha-access-token>` exchanges a token the user
already obtained from Mesha for connector-specific credentials via `POST /api/connector/auth/login`,
then stores them locally. Subsequent backend calls made through the `backendApiRestClient` bean
are authenticated automatically by `ConnectorAuthInterceptor`, which refreshes the access token
via `POST /api/connector/auth/refresh` when it's close to expiry.
