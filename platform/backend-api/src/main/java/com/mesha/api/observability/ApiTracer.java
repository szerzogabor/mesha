package com.mesha.api.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Central observability utility for the backend-api service.
 *
 * Uses the Micrometer OTel bridge for span creation so traces are exported to
 * Grafana Tempo via the OpenTelemetry Java Agent. Sentry is retained for error
 * capture and breadcrumbs only (not for span/transaction management).
 *
 * Covers:
 *   - Generic operation tracing
 *   - GitHub webhook receipt and processing
 *   - GitHub PR workflows
 *   - Repository synchronisation
 *   - AI draft workflows
 *   - Auth flow failures
 */
@Component
public class ApiTracer {

    private static final Logger log = LoggerFactory.getLogger(ApiTracer.class);
    private static final String MDC_TRACE_ID = "traceId";

    private final Tracer tracer;

    public ApiTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    // -------------------------------------------------------------------------
    // Generic operation tracing
    // -------------------------------------------------------------------------

    /**
     * Wraps arbitrary work in a named OTel span with structured tags.
     * Emits a structured log line on completion and propagates traceId into MDC.
     */
    public <T> T traceOperation(String spanName, String operation,
                                Map<String, String> tags, Callable<T> work) {
        Span span = tracer.nextSpan().name(spanName);
        tags.forEach(span::tag);
        span.start();

        long startMs = System.currentTimeMillis();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            setTraceIdMdc();
            T result = work.call();
            return result;
        } catch (Exception e) {
            span.error(e);
            Sentry.captureException(e);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("operation_complete name={} operation={} duration_ms={}",
                    spanName, operation, durationMs);
            span.end();
            MDC.remove(MDC_TRACE_ID);
        }
    }

    // -------------------------------------------------------------------------
    // GitHub webhook
    // -------------------------------------------------------------------------

    /**
     * Records receipt of a GitHub webhook event and sets event-scoped MDC keys
     * for downstream log lines within the same call stack.
     */
    public void recordGitHubWebhookReceived(String githubEventId, String eventType,
                                             String installationId, String repositoryFullName) {
        MDC.put("githubEventId", githubEventId);
        MDC.put("installationId", installationId);
        MDC.put("githubRepository", repositoryFullName);
        setTraceIdMdc();
        Sentry.configureScope(scope -> {
            scope.setTag("github.event_id", githubEventId);
            scope.setTag("github.event_type", eventType);
            scope.setTag("github.installation_id", installationId);
            scope.setTag("github.repository", repositoryFullName);
        });
        log.info("github_webhook_received github_event_id={} event_type={} installation_id={} repository={}",
                githubEventId, eventType, installationId, repositoryFullName);
    }

    /** Records successful processing of a GitHub webhook event and clears MDC keys. */
    public void recordGitHubWebhookProcessed(String githubEventId, String outcome, long durationMs) {
        setTraceIdMdc();
        log.info("github_webhook_processed github_event_id={} outcome={} duration_ms={}",
                githubEventId, outcome, durationMs);
        MDC.remove("githubEventId");
        MDC.remove("installationId");
        MDC.remove("githubRepository");
        MDC.remove(MDC_TRACE_ID);
    }

    /** Records a GitHub webhook processing failure and reports to Sentry. */
    public void captureGitHubWebhookFailure(String githubEventId, String eventType, Throwable error) {
        MDC.put("githubEventId", githubEventId);
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("github.event_id", githubEventId);
            scope.setTag("github.event_type", eventType);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });
        log.error("github_webhook_failure github_event_id={} event_type={}",
                githubEventId, eventType, error);
        MDC.remove("githubEventId");
        MDC.remove("installationId");
        MDC.remove("githubRepository");
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // GitHub PR workflows
    // -------------------------------------------------------------------------

    /**
     * Records the start of a GitHub PR workflow (e.g. review, label, comment).
     * Leaves installationId, githubRepository, and prNumber in MDC for subsequent log lines.
     */
    public void recordGitHubPrWorkflowStart(String installationId, String repositoryFullName,
                                             String prNumber, String eventType) {
        MDC.put("installationId", installationId);
        MDC.put("githubRepository", repositoryFullName);
        MDC.put("prNumber", prNumber);
        setTraceIdMdc();
        Sentry.configureScope(scope -> {
            scope.setTag("github.installation_id", installationId);
            scope.setTag("github.repository", repositoryFullName);
            scope.setTag("github.pr_number", prNumber);
            scope.setTag("github.event_type", eventType);
        });
        log.info("github_pr_workflow_start installation_id={} repository={} pr_number={} event_type={}",
                installationId, repositoryFullName, prNumber, eventType);
    }

    /** Records the successful completion of a GitHub PR workflow and clears PR-scoped MDC keys. */
    public void recordGitHubPrWorkflowComplete(String installationId, String repositoryFullName,
                                                String prNumber, String outcome, long durationMs) {
        MDC.put("installationId", installationId);
        MDC.put("githubRepository", repositoryFullName);
        MDC.put("prNumber", prNumber);
        setTraceIdMdc();
        log.info("github_pr_workflow_complete installation_id={} repository={} pr_number={} outcome={} duration_ms={}",
                installationId, repositoryFullName, prNumber, outcome, durationMs);
        MDC.remove("installationId");
        MDC.remove("githubRepository");
        MDC.remove("prNumber");
        MDC.remove(MDC_TRACE_ID);
    }

    /** Records a GitHub PR workflow failure and reports to Sentry. */
    public void captureGitHubPrWorkflowFailure(String installationId, String repositoryFullName,
                                                String prNumber, Throwable error) {
        MDC.put("installationId", installationId);
        MDC.put("githubRepository", repositoryFullName);
        MDC.put("prNumber", prNumber);
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("github.installation_id", installationId);
            scope.setTag("github.repository", repositoryFullName);
            scope.setTag("github.pr_number", prNumber);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });
        log.error("github_pr_workflow_failure installation_id={} repository={} pr_number={}",
                installationId, repositoryFullName, prNumber, error);
        MDC.remove("installationId");
        MDC.remove("githubRepository");
        MDC.remove("prNumber");
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // Repository synchronisation
    // -------------------------------------------------------------------------

    /**
     * Records the start of a repository sync.
     * Leaves installationId and githubRepository in MDC for subsequent log lines.
     */
    public void recordRepositorySyncStart(String installationId, String repositoryFullName,
                                           String syncReason) {
        MDC.put("installationId", installationId);
        MDC.put("githubRepository", repositoryFullName);
        setTraceIdMdc();
        Sentry.configureScope(scope -> {
            scope.setTag("github.installation_id", installationId);
            scope.setTag("github.repository", repositoryFullName);
            scope.setTag("sync.reason", syncReason);
        });
        log.info("repository_sync_start installation_id={} repository={} reason={}",
                installationId, repositoryFullName, syncReason);
    }

    /** Records the successful completion of a repository sync and clears repo-scoped MDC keys. */
    public void recordRepositorySyncComplete(String installationId, String repositoryFullName,
                                              long durationMs) {
        MDC.put("installationId", installationId);
        MDC.put("githubRepository", repositoryFullName);
        setTraceIdMdc();
        log.info("repository_sync_complete installation_id={} repository={} duration_ms={}",
                installationId, repositoryFullName, durationMs);
        MDC.remove("installationId");
        MDC.remove("githubRepository");
        MDC.remove(MDC_TRACE_ID);
    }

    /** Records a repository sync failure and reports to Sentry. */
    public void captureRepositorySyncFailure(String installationId, String repositoryFullName,
                                              Throwable error) {
        MDC.put("installationId", installationId);
        MDC.put("githubRepository", repositoryFullName);
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("github.installation_id", installationId);
            scope.setTag("github.repository", repositoryFullName);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });
        log.error("repository_sync_failure installation_id={} repository={}",
                installationId, repositoryFullName, error);
        MDC.remove("installationId");
        MDC.remove("githubRepository");
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // AI draft workflows
    // -------------------------------------------------------------------------

    /**
     * Records the start of an AI draft generation workflow.
     * Leaves draftId and aiProvider in MDC for subsequent log lines.
     */
    public void recordAIDraftStarted(String draftId, String aiProvider, String issueId) {
        MDC.put("draftId", draftId);
        MDC.put("aiProvider", aiProvider);
        setTraceIdMdc();
        Sentry.configureScope(scope -> {
            scope.setTag("ai.draft_id", draftId);
            scope.setTag("ai.provider", aiProvider);
            scope.setTag("ai.issue_id", issueId);
        });
        log.info("ai_draft_started draft_id={} provider={} issue_id={}",
                draftId, aiProvider, issueId);
    }

    /** Records the successful completion of an AI draft and clears draft-scoped MDC keys. */
    public void recordAIDraftCompleted(String draftId, String aiProvider, long durationMs) {
        MDC.put("draftId", draftId);
        MDC.put("aiProvider", aiProvider);
        setTraceIdMdc();
        log.info("ai_draft_completed draft_id={} provider={} duration_ms={}",
                draftId, aiProvider, durationMs);
        MDC.remove("draftId");
        MDC.remove("aiProvider");
        MDC.remove(MDC_TRACE_ID);
    }

    /** Records an AI draft generation failure and reports to Sentry. */
    public void captureAIDraftFailure(String draftId, String aiProvider, Throwable error) {
        MDC.put("draftId", draftId);
        MDC.put("aiProvider", aiProvider);
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("ai.draft_id", draftId);
            scope.setTag("ai.provider", aiProvider);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });
        log.error("ai_draft_failure draft_id={} provider={}", draftId, aiProvider, error);
        MDC.remove("draftId");
        MDC.remove("aiProvider");
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // Auth flows
    // -------------------------------------------------------------------------

    /** Records an auth failure (expired token, invalid claims, etc.) for alerting and audit. */
    public void captureAuthFailure(String reason, String path, Throwable error) {
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("auth.failure_reason", reason);
            scope.setTag("auth.path", path);
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureException(error);
        });
        log.warn("auth_failure reason={} path={}", reason, path, error);
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void setTraceIdMdc() {
        Span current = tracer.currentSpan();
        if (current != null) {
            String traceId = current.context().traceId();
            if (traceId != null && !traceId.isBlank()) {
                MDC.put(MDC_TRACE_ID, traceId);
            }
        }
    }
}
