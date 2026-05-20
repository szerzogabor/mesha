package com.mesha.worker.observability;

import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SpanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Central observability utility for the worker service.
 *
 * Provides Sentry tracing, structured MDC logging, and metrics emission for:
 *   - AI provider session creation and polling
 *   - Orchestration state transitions
 *   - Queue job processing
 *   - GitHub webhook retries
 *   - Timeout and cancellation events
 *   - Execution recovery
 *
 * All public methods are safe to call from async/scheduled contexts where no
 * active Sentry transaction exists; they create a root transaction in that case.
 */
@Component
public class WorkflowTracer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTracer.class);

    // -------------------------------------------------------------------------
    // Generic operation tracing
    // -------------------------------------------------------------------------

    /**
     * Traces an arbitrary operation, creating a new Sentry transaction (or a child span
     * if one is already active), emitting a structured log line, and recording duration.
     */
    public <T> T traceOperation(String transactionName, String operation,
                                Map<String, String> tags, Callable<T> work) {
        ISpan span = activeSpanOrNewTransaction(transactionName, operation);
        tags.forEach(span::setTag);

        long startMs = System.currentTimeMillis();
        try {
            T result = work.call();
            span.setStatus(SpanStatus.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(SpanStatus.INTERNAL_ERROR);
            span.setThrowable(e);
            if (!(e instanceof RuntimeException)) {
                throw new RuntimeException(e);
            }
            throw (RuntimeException) e;
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            span.setData("duration_ms", durationMs);
            span.finish();
            Sentry.metrics().distribution("worker.operation.duration_ms", durationMs,
                    "operation:" + operation);
            log.info("operation_complete name={} operation={} duration_ms={} status={}",
                    transactionName, operation, durationMs, span.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // AI workflow instrumentation
    // -------------------------------------------------------------------------

    /**
     * Records an orchestration state transition with full context in MDC and Sentry scope.
     */
    public void recordOrchestrationTransition(String workflowId, String fromState, String toState) {
        MDC.put("workflowId", workflowId);
        MDC.put("orchestrationState", toState);
        Sentry.configureScope(scope -> {
            scope.setTag("workflow.id", workflowId);
            scope.setTag("orchestration.state", toState);
        });
        Sentry.metrics().count("workflow.state_transition", 1.0,
                "from:" + fromState, "to:" + toState);
        log.info("orchestration_transition workflow_id={} from={} to={}",
                workflowId, fromState, toState);
    }

    /**
     * Captures an AI provider failure and emits failure metrics.
     */
    public void captureAiProviderFailure(String provider, String operation,
                                         int retryCount, Throwable error) {
        MDC.put("provider", provider);
        MDC.put("retryCount", String.valueOf(retryCount));

        Sentry.withScope(scope -> {
            scope.setTag("ai.provider", provider);
            scope.setTag("ai.operation", operation);
            scope.setExtra("retry.count", retryCount);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });

        Sentry.metrics().count("ai.provider.failure", 1.0,
                "provider:" + provider, "operation:" + operation);

        log.error("ai_provider_failure provider={} operation={} retry_count={}",
                provider, operation, retryCount, error);
        MDC.remove("provider");
        MDC.remove("retryCount");
    }

    /**
     * Records a polling failure (e.g. Blocks session status check failed).
     */
    public void capturePollingFailure(String provider, String sessionId,
                                      int pollAttempt, Throwable error) {
        MDC.put("sessionId", sessionId);
        MDC.put("retryCount", String.valueOf(pollAttempt));

        Sentry.withScope(scope -> {
            scope.setTag("ai.provider", provider);
            scope.setTag("session.id", sessionId);
            scope.setExtra("poll.attempt", pollAttempt);
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureException(error);
        });

        Sentry.metrics().count("ai.session.poll_failure", 1.0, "provider:" + provider);
        log.warn("polling_failure provider={} session_id={} poll_attempt={}",
                provider, sessionId, pollAttempt, error);
        MDC.remove("sessionId");
        MDC.remove("retryCount");
    }

    // -------------------------------------------------------------------------
    // Queue and background job tracing
    // -------------------------------------------------------------------------

    /**
     * Captures a queue job processing failure.
     */
    public void captureQueueFailure(String jobId, String jobType, Throwable error) {
        MDC.put("jobId", jobId);

        Sentry.withScope(scope -> {
            scope.setTag("queue.job_id", jobId);
            scope.setTag("queue.job_type", jobType);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });

        Sentry.metrics().count("queue.job.failure", 1.0, "job_type:" + jobType);
        log.error("queue_failure job_id={} job_type={}", jobId, jobType, error);
        MDC.remove("jobId");
    }

    /**
     * Records a retry attempt for any worker context.
     */
    public void recordRetry(String context, String contextId, int retryCount) {
        MDC.put("retryCount", String.valueOf(retryCount));
        Sentry.configureScope(scope -> {
            scope.setExtra("retry.context", context);
            scope.setExtra("retry.context_id", contextId);
            scope.setExtra("retry.count", retryCount);
        });
        Sentry.metrics().count("worker.retry", 1.0, "context:" + context);
        log.warn("retry_attempt context={} context_id={} retry_count={}",
                context, contextId, retryCount);
        MDC.remove("retryCount");
    }

    // -------------------------------------------------------------------------
    // GitHub webhook tracing
    // -------------------------------------------------------------------------

    /**
     * Captures a GitHub webhook retry failure (all retries exhausted).
     */
    public void captureWebhookRetryFailure(String githubEventId, int retryCount, Throwable error) {
        MDC.put("githubEventId", githubEventId);
        MDC.put("retryCount", String.valueOf(retryCount));

        Sentry.withScope(scope -> {
            scope.setTag("github.event_id", githubEventId);
            scope.setExtra("retry.count", retryCount);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });

        Sentry.metrics().count("webhook.retry_failure", 1.0);
        log.error("webhook_retry_failure github_event_id={} retry_count={}",
                githubEventId, retryCount, error);
        MDC.remove("githubEventId");
        MDC.remove("retryCount");
    }

    // -------------------------------------------------------------------------
    // Timeout and cancellation events
    // -------------------------------------------------------------------------

    /**
     * Records a timeout event and sends a Sentry warning.
     */
    public void captureTimeoutEvent(String operation, String contextId, long durationMs) {
        Sentry.withScope(scope -> {
            scope.setTag("timeout.operation", operation);
            scope.setExtra("timeout.context_id", contextId);
            scope.setExtra("timeout.duration_ms", durationMs);
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureMessage("Timeout: " + operation + " [" + contextId + "]");
        });
        Sentry.metrics().distribution("worker.timeout_ms", durationMs, "operation:" + operation);
        log.warn("timeout_event operation={} context_id={} duration_ms={}",
                operation, contextId, durationMs);
    }

    /**
     * Records a workflow cancellation event.
     */
    public void recordCancellation(String workflowId, String reason) {
        MDC.put("workflowId", workflowId);
        Sentry.withScope(scope -> {
            scope.setTag("workflow.id", workflowId);
            scope.setTag("cancellation.reason", reason);
            scope.setLevel(SentryLevel.INFO);
            Sentry.captureMessage("Workflow cancelled: " + workflowId + " — " + reason);
        });
        Sentry.metrics().count("workflow.cancelled", 1.0);
        log.info("workflow_cancelled workflow_id={} reason={}", workflowId, reason);
        MDC.remove("workflowId");
    }

    /**
     * Records execution recovery (e.g. a job restarted after a crash).
     */
    public void recordExecutionRecovery(String contextId, String recoveredFrom) {
        Sentry.withScope(scope -> {
            scope.setTag("recovery.context_id", contextId);
            scope.setTag("recovery.from", recoveredFrom);
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureMessage("Execution recovery: " + contextId);
        });
        Sentry.metrics().count("worker.recovery", 1.0);
        log.warn("execution_recovery context_id={} recovered_from={}", contextId, recoveredFrom);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private ISpan activeSpanOrNewTransaction(String name, String operation) {
        ISpan current = Sentry.getSpan();
        if (current != null && !current.isFinished()) {
            return current.startChild(operation, name);
        }
        ITransaction tx = Sentry.startTransaction(name, operation);
        tx.setName(name);
        return tx;
    }
}
