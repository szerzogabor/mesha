# Observability

## Purpose

Provide full-stack visibility into the Mesha platform — distributed traces, structured logs, and metrics — so that engineers can diagnose issues in production without adding debug instrumentation after the fact.

---

## Responsibilities

- Propagate correlation IDs across frontend, backend-api, and backend-worker
- Export distributed traces (OpenTelemetry) to Grafana Cloud (Tempo)
- Export structured logs (JSON) to Grafana Cloud (Loki)
- Export metrics to Grafana Cloud (Prometheus)
- Track AI workflow events with structured log entries

---

## Components

### Frontend
- OpenTelemetry SDK initialized in `src/lib/otel/`
- Browser traces sent to Grafana Cloud (Tempo) via OTLP/HTTP
- `X-Correlation-ID` header injected into all API calls via `api-client.ts`
- Structured client-side logging via `src/lib/logger.ts`

### Backend API
- OpenTelemetry Java Agent (`-javaagent:opentelemetry-javaagent.jar`)
- Service name: `mesha-api` (`OTEL_SERVICE_NAME`)
- Traces exported to Grafana Cloud (Tempo) via OTLP gRPC
- Structured JSON logs via Logstash encoder + Loki4j appender (Loki push)
- `CorrelationIdFilter` — reads `X-Correlation-ID` from request headers, stores in MDC

### Backend Worker
- Same OTel Java Agent setup as backend-api
- Service name: `mesha-worker`
- `WorkflowTracer` — structured JSON log entries for AI session state transitions

### Grafana Cloud
- **Tempo:** distributed traces (spans from frontend + backend-api + backend-worker)
- **Loki:** structured log aggregation from all services
- **Prometheus:** JVM metrics (heap, GC, threads), HTTP request rates, DB pool metrics
- **Dashboards:** custom dashboards in `platform/infrastructure/grafana/`

---

## Public Interfaces

### Health Check
```
GET /actuator/health
```
Returns Spring Boot Actuator health status. Used by Render for health checks.

### Metrics
```
GET /actuator/prometheus
```
Prometheus-format metrics (protected in production, scraping configured separately).

---

## Dependencies

| Dependency | Purpose |
|-----------|---------|
| `opentelemetry-javaagent` | Auto-instrumentation (HTTP, JDBC, Redis) |
| `micrometer-registry-prometheus` | JVM metrics |
| `loki4j-logback-appender` | Push logs to Loki |
| `logstash-logback-encoder` | JSON log format |
| `@opentelemetry/*` npm packages | Frontend SDK |
| `OTEL_EXPORTER_OTLP_ENDPOINT` env var | Grafana Cloud OTLP endpoint |
| `OTEL_EXPORTER_OTLP_HEADERS` env var | Grafana Cloud auth header |

---

## Important Business Rules

1. **Correlation IDs:** Every frontend HTTP request generates a `X-Correlation-ID` UUID. This is propagated to backend-api and logged in MDC so all log lines for a single request are correlated.

2. **Structured logging:** All log output is JSON. Never use unstructured `System.out.println`. Use `log.info("message", keyValuePairs...)` patterns.

3. **WorkflowTracer:** AI session state transitions are logged with structured fields (`sessionId`, `fromState`, `toState`, `issueId`, `workspaceId`) so they can be queried in Loki.

4. **Service name disambiguation:** `mesha-api` and `mesha-worker` have distinct `OTEL_SERVICE_NAME` values so traces and logs can be filtered per service.

5. **No secrets in logs:** Never log Blocks API keys, Clerk JWTs, webhook payloads with sensitive content, or GitHub private key material.

6. **Grafana dashboards as code:** Dashboard JSON configs live in `platform/infrastructure/grafana/`. Do not create dashboards manually in the UI without also exporting and committing the JSON.

---

## Related Feature Specifications

- No user-visible features — this is infrastructure.
