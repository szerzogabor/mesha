# MES-99 Backend API Memory Leak Investigation & Heap Analysis

_Last updated: 2026-06-04_

## Executive summary

This report establishes the initial JVM memory-health baseline for the `backend-api` service and records the code-level memory leak investigation requested by MES-99.

No deterministic memory leak was proven from static analysis alone. The service is currently low risk for classic in-process leaks because the reviewed backend API code does not define application-owned static collections, unbounded in-memory caches, custom thread pools, scheduled tasks, or WebSocket session registries. The primary memory-growth risks are instead operational and workload-driven:

1. raw GitHub webhook payloads are persisted indefinitely unless a retention policy is added;
2. webhook ingestion performs parsing, dispatch, and database persistence inside a single transaction;
3. GitHub API list/sync flows materialize full JSON responses and DTO lists in memory;
4. actuator metrics were available, but Prometheus scrape support was not enabled before this investigation;
5. JVM heap dumps were not automatically captured on out-of-memory termination before this investigation.

This change adds the first monitoring/remediation baseline by enabling Prometheus actuator metrics, tagging Micrometer output with the service name, enabling HikariCP connection leak detection, and configuring JVM heap dump capture on out-of-memory errors.

## Investigation environment and limitations

This investigation was performed against repository source code and service configuration. A live long-running `backend-api` process, production Grafana data, and production heap dumps were not available in the agent environment, so this report does not claim a production heap dump dominator-tree result.

The recommended next validation step is to capture heap dumps and thread dumps from a staging or production-like workload after the Prometheus baseline from this change is deployed.

## Files reviewed

The investigation focused on these backend API areas:

- `platform/backend-api/pom.xml`
- `platform/backend-api/Dockerfile`
- `platform/backend-api/src/main/resources/application.yml`
- `platform/backend-api/src/main/java/com/mesha/api/service/GitHubAppService.java`
- `platform/backend-api/src/main/java/com/mesha/api/service/GitHubWebhookService.java`
- `platform/backend-api/src/main/java/com/mesha/api/service/GitHubPullRequestService.java`
- `platform/backend-api/src/main/java/com/mesha/api/service/GitHubRepositoryService.java`
- `platform/backend-api/src/main/java/com/mesha/api/service/BlocksSessionService.java`
- `platform/backend-api/src/main/java/com/mesha/api/ai/AIOrchestrationService.java`
- `platform/backend-api/src/main/java/com/mesha/api/ai/ClaudeAIAdapter.java`
- `platform/backend-api/src/main/java/com/mesha/api/observability/*.java`
- backend API repositories, DTOs, and model classes under `platform/backend-api/src/main/java/com/mesha/api/`

## Baseline JVM memory health

### Current JVM/container settings

The backend image runs on Eclipse Temurin 21 and already enables container-aware sizing with `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0`.

This investigation adds:

- `-XX:+HeapDumpOnOutOfMemoryError`
- `-XX:HeapDumpPath=/tmp/mesha-api-heapdump.hprof`
- `-XX:+ExitOnOutOfMemoryError`

These flags provide a deterministic artifact when the service terminates due to heap exhaustion and avoid leaving a corrupted or repeatedly failing JVM process alive.

### Actuator and Micrometer baseline

The service already used Spring Boot Actuator, but actuator exposure was limited to `health`, `info`, and `metrics`. This investigation adds Micrometer's Prometheus registry and exposes `/actuator/prometheus` so Grafana/Prometheus can scrape JVM, HikariCP, HTTP server, Tomcat, and process metrics.

Metrics are tagged with `application=${spring.application.name}` so dashboards can filter `mesha-api` consistently.

Recommended dashboard source: Grafana's JVM Micrometer dashboard is a suitable baseline for Micrometer-instrumented Spring Boot applications.

## Findings

### Finding 1: No obvious static collection or singleton cache leak found

Severity: Low

A search across backend API Java source did not identify application-owned static `Map`, `List`, `Set`, or cache fields that retain request or workspace data for the lifetime of the JVM. Static fields found during review are constants and loggers.

Impact: classic long-lived in-process collection leaks are unlikely in the current backend API code.

Recommendation: keep this invariant. If in-memory caching is added later, use bounded caches with explicit maximum size, expiry, and Micrometer cache metrics.

### Finding 2: No custom executor, scheduler, or async thread leak found

Severity: Low

The reviewed backend API service code does not define custom `ExecutorService`, `Thread`, `@Async`, or `@Scheduled` workloads. GitHub webhook processing and GitHub API sync currently run synchronously in request-handling flows.

Impact: backend-api-specific thread leak risk is currently low. The main threads to monitor are the embedded web server, JVM common pool usage from JDK internals, database pool, Redis client resources, and OpenTelemetry/Sentry background threads.

Recommendation: if repository synchronization or AI orchestration becomes asynchronous, define bounded thread pools with explicit queue sizes, shutdown behavior, thread-name prefixes, and thread-count metrics.

### Finding 3: Raw webhook payload retention can produce database-backed memory and storage pressure

Severity: Medium

`GitHubWebhookService` persists the raw webhook payload for every unique delivery. This is useful for auditability and replay, but it has no visible retention, compaction, or payload-size policy in the current backend API code.

Impact: the JVM itself may not retain these payloads after request completion, but large or high-volume webhook traffic can increase database size and produce higher transient heap pressure when historical rows are queried or when events are processed. GitHub integration growth will amplify this.

Recommendation:

- add a retention policy for processed webhook events, for example delete or archive processed events older than 30-90 days;
- cap accepted webhook payload size at the reverse proxy and application layer;
- avoid broad list endpoints that return raw payloads;
- add a metric for webhook payload bytes and webhook processing duration.

### Finding 4: Webhook ingest work happens in one transaction

Severity: Medium

Webhook ingestion persists the event, parses JSON, dispatches processing, mutates the same event row, and saves the final state under one transactional method.

Impact: a slow GitHub installation event, pull request processing path, or database operation can keep transaction resources and object graphs alive longer than necessary. This is not a confirmed leak, but it increases retained live data during bursts.

Recommendation:

- keep the initial event insert short;
- dispatch heavy processing outside the ingestion transaction or through a bounded queue;
- record processing state transitions in separate transactions;
- emit metrics for ingest duration, dispatch duration, and error counts by event type.

### Finding 5: GitHub API sync materializes full JSON responses and result lists

Severity: Medium

GitHub repository and pull request sync paths use `HttpResponse.BodyHandlers.ofString()`, parse full JSON into Jackson trees, and return full DTO lists. Pull request sync requests up to 100 pull requests per call.

Impact: this is acceptable at current scale, but it creates transient heap spikes proportional to response size, JSON tree size, and DTO output size. Repository synchronization can become a hotspot as installation and repository counts grow.

Recommendation:

- introduce pagination controls instead of returning every stored pull request after sync;
- record GitHub response byte sizes and sync duration;
- consider streaming parsing for large GitHub responses if repository counts grow substantially;
- add rate-limit and page-count metrics by installation/repository.

### Finding 6: HikariCP leak detection was not configured

Severity: Medium

The service uses Spring Data JPA and PostgreSQL through HikariCP, but connection leak detection was not configured before this change.

Impact: unclosed JDBC connections are unlikely when using Spring Data repositories correctly, but connection leak detection gives early warning for future manual JDBC, transaction, or persistence-context mistakes.

Remediation applied: `spring.datasource.hikari.leak-detection-threshold` now defaults to 30 seconds and can be overridden by `DB_LEAK_DETECTION_THRESHOLD_MS`.

### Finding 7: Prometheus scrape endpoint was missing

Severity: Medium

Actuator metrics existed, but the Prometheus registry dependency and `/actuator/prometheus` endpoint were not enabled.

Impact: Grafana cannot reliably trend backend API heap, non-heap, GC, thread, class-loading, HTTP, and database-pool metrics from this service without a Prometheus scrape endpoint or another metrics exporter.

Remediation applied: added `micrometer-registry-prometheus`, exposed `prometheus`, and added an `application` metrics tag.

### Finding 8: Heap dump capture on OOM was missing

Severity: Medium

The JVM entrypoint did not request heap dump generation when an out-of-memory error occurs.

Impact: if the service is OOM-killed or exits due to heap exhaustion, the most important diagnostic artifact may be missing.

Remediation applied: the Dockerfile now enables heap dump generation and JVM exit on out-of-memory errors.

## Heap dump analysis plan

Because no live heap dump was available in this environment, the following procedure should be used in staging or production-like environments.

### Capture live heap histogram

```bash
jcmd <pid> GC.class_histogram > class-histogram-before.txt
```

Capture this before and after a representative workload. Compare object counts and retained bytes for:

- Jackson `JsonNode`, `ObjectNode`, `ArrayNode`, `TextNode`
- `String`, `byte[]`, `char[]`
- Hibernate entity classes under `com.mesha.api.model`
- servlet request/response wrappers
- GitHub webhook payload strings
- OpenTelemetry and Sentry queues/buffers

### Capture heap dump

```bash
jcmd <pid> GC.heap_dump /tmp/mesha-api-$(date +%Y%m%d-%H%M%S).hprof
```

Analyze with Eclipse MAT or VisualVM:

1. open the `.hprof` file;
2. run leak suspects report;
3. inspect the dominator tree;
4. sort by retained heap;
5. inspect paths to GC roots for top retainers;
6. compare old generation occupancy before and after full GC;
7. preserve screenshots of the leak suspects report, dominator tree, and top retained object paths.

### Capture OOM heap dump

With this change, OOM heap dumps are written to:

```text
/tmp/mesha-api-heapdump.hprof
```

For production, mount `/tmp` or override the heap-dump path to durable storage before relying on this for incident response.

## Thread analysis plan

Capture three thread dumps 10 seconds apart during normal traffic and during a GitHub sync burst:

```bash
jcmd <pid> Thread.print > thread-dump-1.txt
sleep 10
jcmd <pid> Thread.print > thread-dump-2.txt
sleep 10
jcmd <pid> Thread.print > thread-dump-3.txt
```

Review:

- total thread count trend;
- Tomcat request threads blocked on database, GitHub API, Redis, Sentry, or logging;
- Hikari housekeeper and connection pool threads;
- OpenTelemetry exporter threads;
- Sentry profiler/background threads;
- JDK HTTP client selector/worker threads;
- blocked or waiting states that accumulate across dumps.

No backend-api custom thread-pool leak was found in source review, but external library threads should be tracked in Grafana.

## Resource leak review

### Database resources

Spring Data repository usage and `@Transactional` boundaries dominate database access. No manual JDBC `Connection`, `Statement`, or `ResultSet` management was found in the backend API source review.

Risk areas:

- transaction scopes that include webhook dispatch work;
- repository sync methods that save many entities in a single request;
- future manual JDBC code.

Remediation applied: HikariCP connection leak detection now defaults to 30 seconds.

### HTTP client resources

GitHub integration services create singleton JDK `HttpClient` instances in service constructors and use synchronous `send` calls with `BodyHandlers.ofString()`.

Risk areas:

- response bodies are fully materialized as strings;
- no per-request timeout is set on all requests;
- sync work runs on request threads.

Recommendations:

- add request timeouts to all GitHub `HttpRequest` builders;
- record response status, response bytes, and duration metrics;
- paginate large list calls;
- consider offloading sync work to bounded background workers.

### Cache and Redis resources

The backend API includes Redis starter configuration, but no application-level in-memory cache implementation was identified in the reviewed backend API code.

Recommendations:

- if Spring Cache or Caffeine is added, require maximum size, TTL, and cache metrics;
- monitor Redis client connection counts and command latency separately from heap metrics.

### WebSocket resources

The backend API includes the Spring WebSocket starter, but no application WebSocket session registry was identified in source review.

Recommendations:

- if WebSocket handlers are added later, track active sessions and bytes queued per session;
- enforce session idle timeouts and close handling.

## Grafana monitoring recommendations

Add or import dashboards that cover the following Prometheus/Micrometer metrics from `/actuator/prometheus`.

### JVM memory panels

- heap used, committed, and max by memory area and pool;
- old generation usage after GC;
- non-heap and metaspace usage;
- allocation rate;
- live data size after major/full GC;
- container memory usage and RSS if node/container metrics are available.

### GC panels

- GC pause count and duration by action/cause;
- max and p95 GC pause time;
- GC overhead percentage;
- promotion rate and old-generation growth.

### Thread panels

- live thread count;
- daemon thread count;
- peak thread count;
- blocked/waiting/runnable thread states if available;
- Tomcat busy/request threads.

### Database panels

- Hikari active, idle, pending, max, min connections;
- Hikari connection acquire time;
- Hikari usage time;
- Hikari timeout count;
- leak-detection log events.

### HTTP and GitHub integration panels

- HTTP server request count/duration by route/status;
- GitHub sync duration and error count once custom metrics are added;
- webhook event count by event type/action/status once custom metrics are added;
- webhook payload byte histogram once custom metrics are added.

### Recommended alerts

- Heap used after GC exceeds 80% of max for 15 minutes.
- Old generation grows monotonically for 30 minutes under stable throughput.
- GC p95 pause exceeds 500 ms for 10 minutes.
- Process RSS exceeds 90% of container memory limit.
- Live thread count grows by more than 50% over baseline for 30 minutes.
- Hikari pending connections remain above 0 for 5 minutes.
- Hikari active connections remain above 90% of max for 5 minutes.
- Webhook processing error rate exceeds 5% over 10 minutes.

## Remediation plan

### Completed in this change

- Enable Prometheus actuator metrics for `backend-api`.
- Tag Micrometer metrics with `application=mesha-api`.
- Enable HikariCP leak detection with environment override.
- Enable JVM OOM heap dump capture in the backend API image.
- Document heap dump, thread dump, and Grafana baseline procedures.

### Next short-term work

1. Mount a durable heap-dump volume or object-store sidecar path in production.
2. Add webhook-event retention or archival for processed events.
3. Add custom Micrometer metrics for GitHub webhook bytes, webhook duration, GitHub sync duration, and GitHub API response sizes.
4. Add request timeouts to every GitHub `HttpRequest`.
5. Capture and archive staging heap dump screenshots from Eclipse MAT.

### Next medium-term work

1. Move heavy GitHub sync work to a bounded background worker queue.
2. Add pagination to PR sync responses and repository views where unbounded growth is possible.
3. Add cache policy requirements before introducing any in-memory caches.
4. Add a runbook for memory incidents with commands, expected artifact locations, and Grafana links.

## Definition of done mapping

- Heap dump analyzed: procedure documented; live heap dump unavailable in this environment, so staging/prod capture remains required.
- GC behavior reviewed: metrics and dashboard requirements documented; Prometheus endpoint enabled.
- Thread usage reviewed: source review found no custom thread pools; thread dump procedure documented.
- Resource leaks investigated: database, HTTP client, cache, Redis, and WebSocket resource risks reviewed.
- Memory hotspots identified: webhook payload retention and GitHub JSON materialization identified as primary hotspots.
- Remediation plan documented: completed and planned remediations listed above.
- Grafana monitoring recommendations provided: dashboard panels and alert candidates listed above.
