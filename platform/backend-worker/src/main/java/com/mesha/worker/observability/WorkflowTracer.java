package com.mesha.worker.observability;

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
 * Central observability utility for the worker service.
 *
 * Uses the Micrometer OTel bridge for span creation so traces are exported to
 * Grafana Tempo via the OpenTelemetry Java Agent. Sentry is retained for error
 * capture and breadcrumbs only (not for span/transaction management).
 *
 * Covers:
 *   - Generic operation tracing
 *   - Orchestration state transitions
 *   - AI provider session creation and polling
 *   - Queue job processing
 *   - GitHub PR workflows
 *   - Repository synchronisation
 *   - Webhook-triggered workflows
 *   - Retry, timeout, and cancellation flows
 */
@Component
public class WorkflowTracer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTracer.class);
    private static final String MDC_TRACE_ID = "traceId";

    private final Tracer tracer;

    public WorkflowTracer(Tracer tracer) {
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
    // Orchestration
    // -------------------------------------------------------------------------

    public void recordOrchestrationTransition(String workflowId, String fromState, String toState) {
        MDC.put("workflowId", workflowId);
        MDC.put("orchestrationState", toState);
        setTraceIdMdc();
        Sentry.configureScope(scope -> {
            scope.setTag("workflow.id", workflowId);
            scope.setTag("orchestration.state", toState);
            scope.setTag("transition.from", fromState);
        });
        log.info("orchestration_transition workflow_id={} from={} to={}",
                workflowId, fromState, toState);
        MDC.remove("orchestrationState");
    }

    // -------------------------------------------------------------------------
    // AI provider
    // -------------------------------------------------------------------------

    public void captureAiProviderFailure(String provider, String operation,
                                         int retryCount, Throwable error) {
        MDC.put("provider", provider);
        MDC.put("retryCount", String.valueOf(retryCount));
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("ai.provider", provider);
            scope.setTag("ai.operation", operation);
            scope.setExtra("retry.count", String.valueOf(retryCount));
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });
        log.error("ai_provider_failure provider={} operation={} retry_count={}",
                provider, operation, retryCount, error);
        MDC.remove("provider");
        MDC.remove("retryCount");
        MDC.remove(MDC_TRACE_ID);
    }

    public void capturePollingFailure(String provider, String sessionId,
                                      int pollAttempt, Throwable error) {
        MDC.put("sessionId", sessionId);
        MDC.put("retryCount", String.valueOf(pollAttempt));
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("ai.provider", provider);
            scope.setTag("session.id", sessionId);
            scope.setExtra("poll.attempt", String.valueOf(pollAttempt));
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureException(error);
        });
        log.warn("polling_failure provider={} session_id={} poll_attempt={}",
                provider, sessionId, pollAttempt, error);
        MDC.remove("sessionId");
        MDC.remove("retryCount");
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // Queue
    // -------------------------------------------------------------------------

    public void captureQueueFailure(String jobId, String jobType, Throwable error) {
        MDC.put("jobId", jobId);
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("queue.job_id", jobId);
            scope.setTag("queue.job_type", jobType);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });
        log.error("queue_failure job_id={} job_type={}", jobId, jobType, error);
        MDC.remove("jobId");
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // Retry / recovery
    // -------------------------------------------------------------------------

    public void recordRetry(String context, String contextId, int retryCount) {
        MDC.put("retryCount", String.valueOf(retryCount));
        setTraceIdMdc();
        Sentry.configureScope(scope -> {
            scope.setExtra("retry.context", context);
            scope.setExtra("retry.context_id", contextId);
            scope.setExtra("retry.count", String.valueOf(retryCount));
        });
        log.warn("retry_attempt context={} context_id={} retry_count={}",
                context, contextId, retryCount);
        MDC.remove("retryCount");
        MDC.remove(MDC_TRACE_ID);
    }

    public void recordExecutionRecovery(String contextId, String recoveredFrom) {
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("recovery.context_id", contextId);
            scope.setTag("recovery.from", recoveredFrom);
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureMessage("Execution recovery: " + contextId);
        });
        log.warn("execution_recovery context_id={} recovered_from={}", contextId, recoveredFrom);
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // Timeout / cancellation
    // -------------------------------------------------------------------------

    public void captureTimeoutEvent(String operation, String contextId, long durationMs) {
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("timeout.operation", operation);
            scope.setExtra("timeout.context_id", contextId);
            scope.setExtra("timeout.duration_ms", String.valueOf(durationMs));
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureMessage("Timeout: " + operation + " [" + contextId + "]");
        });
        log.warn("timeout_event operation={} context_id={} duration_ms={}",
                operation, contextId, durationMs);
        MDC.remove(MDC_TRACE_ID);
    }

    public void recordCancellation(String workflowId, String reason) {
        MDC.put("workflowId", workflowId);
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("workflow.id", workflowId);
            scope.setTag("cancellation.reason", reason);
            scope.setLevel(SentryLevel.INFO);
            Sentry.captureMessage("Workflow cancelled: " + workflowId + " — " + reason);
        });
        log.info("workflow_cancelled workflow_id={} reason={}", workflowId, reason);
        MDC.remove("workflowId");
        MDC.remove(MDC_TRACE_ID);
    }

    // -------------------------------------------------------------------------
    // GitHub webhook retry
    // -------------------------------------------------------------------------

    public void captureWebhookRetryFailure(String githubEventId, int retryCount, Throwable error) {
        MDC.put("githubEventId", githubEventId);
        MDC.put("retryCount", String.valueOf(retryCount));
        setTraceIdMdc();
        Sentry.withScope(scope -> {
            scope.setTag("github.event_id", githubEventId);
            scope.setExtra("retry.count", String.valueOf(retryCount));
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });
        log.error("webhook_retry_failure github_event_id={} retry_count={}",
                githubEventId, retryCount, error);
        MDC.remove("githubEventId");
        MDC.remove("retryCount");
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

    /**
     * Records the successful completion of a GitHub PR workflow and clears PR-scoped MDC keys.
     */
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
     * Records the start of a repository sync (e.g. triggered by installation or push events).
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
    // Webhook-triggered workflows
    // -------------------------------------------------------------------------

    /**
     * Records receipt and queueing of a webhook-triggered workflow.
     * Leaves event-scoped MDC keys set for downstream log lines within the same call stack.
     */
    public void recordWebhookWorkflowTriggered(String githubEventId, String eventType,
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
        log.info("webhook_workflow_triggered github_event_id={} event_type={} installation_id={} repository={}",
                githubEventId, eventType, installationId, repositoryFullName);
    }

    /** Records the completion of a webhook-triggered workflow and clears webhook-scoped MDC keys. */
    public void recordWebhookWorkflowComplete(String githubEventId, String outcome, long durationMs) {
        setTraceIdMdc();
        log.info("webhook_workflow_complete github_event_id={} outcome={} duration_ms={}",
                githubEventId, outcome, durationMs);
        MDC.remove("githubEventId");
        MDC.remove("installationId");
        MDC.remove("githubRepository");
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
