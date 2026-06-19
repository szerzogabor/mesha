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
├── ConnectorApplication.java        # @SpringBootApplication entry point
└── config/
    └── ConnectorProperties.java     # connector.* configuration abstraction
```
