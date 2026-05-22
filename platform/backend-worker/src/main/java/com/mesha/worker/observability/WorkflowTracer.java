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
 *   - Queue job enqueue/dequeue/lifecycle/dead-letter
 *   - GitHub webhook processing and retries
 *   - Branch and PR creation flows
 *   - Installation and repository synchronization
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
            span.setData("duration_ms", (double) durationMs);
            span.finish();
            Sentry.metrics().distribution("worker.operation.duration_ms", (double) durationMs);
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
            scope.setTag("transition.from", fromState);
        });
        Sentry.metrics().count("workflow.state_transition", 1.0);
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
            scope.setExtra("retry.count", String.valueOf(retryCount));
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });

        Sentry.metrics().count("ai.provider.failure", 1.0);

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
            scope.setExtra("poll.attempt", String.valueOf(pollAttempt));
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureException(error);
        });

        Sentry.metrics().count("ai.session.poll_failure", 1.0);
        log.warn("polling_failure provider={} session_id={} poll_attempt={}",
                provider, sessionId, pollAttempt, error);
        MDC.remove("sessionId");
        MDC.remove("retryCount");
    }

    /**
     * Records a session lifecycle event (created, started, completed, failed).
     */
    public void recordSessionLifecycle(String sessionId, String provider, String event) {
        MDC.put("sessionId", sessionId);
        MDC.put("provider", provider);
        Sentry.configureScope(scope -> {
            scope.setTag("session.id", sessionId);
            scope.setTag("ai.provider", provider);
            scope.setTag("session.event", event);
        });
        Sentry.metrics().count("ai.session." + event, 1.0);
        log.info("session_lifecycle session_id={} provider={} event={}", sessionId, provider, event);
        MDC.remove("sessionId");
        MDC.remove("provider");
    }

    /**
     * Records the completion of an AI session with timing.
     */
    public void recordSessionComplete(String sessionId, String provider, long durationMs, String status) {
        MDC.put("sessionId", sessionId);
        MDC.put("provider", provider);
        Sentry.metrics().distribution("ai.session.duration_ms", (double) durationMs);
        Sentry.metrics().count("ai.session.complete", 1.0);
        log.info("session_complete session_id={} provider={} duration_ms={} status={}",
                sessionId, provider, durationMs, status);
        MDC.remove("sessionId");
        MDC.remove("provider");
    }

    // -------------------------------------------------------------------------
    // Queue and background job tracing
    // -------------------------------------------------------------------------

    /**
     * Records a job being enqueued.
     */
    public void recordQueueEnqueue(String jobId, String jobType, Map<String, String> metadata) {
        MDC.put("jobId", jobId);
        MDC.put("jobType", jobType);
        Sentry.configureScope(scope -> {
            scope.setTag("queue.job_id", jobId);
            scope.setTag("queue.job_type", jobType);
            scope.setTag("queue.operation", "enqueue");
        });
        Sentry.metrics().count("queue.enqueue", 1.0);
        log.info("queue_enqueue job_id={} job_type={} metadata={}", jobId, jobType, metadata);
        MDC.remove("jobId");
        MDC.remove("jobType");
    }

    /**
     * Records a job being dequeued and starting processing.
     */
    public void recordQueueDequeue(String jobId, String jobType, long queueTimeMs) {
        MDC.put("jobId", jobId);
        MDC.put("jobType", jobType);
        Sentry.configureScope(scope -> {
            scope.setTag("queue.job_id", jobId);
            scope.setTag("queue.job_type", jobType);
            scope.setTag("queue.operation", "dequeue");
        });
        Sentry.metrics().distribution("queue.wait_time_ms", (double) queueTimeMs);
        log.info("queue_dequeue job_id={} job_type={} queue_time_ms={}", jobId, jobType, queueTimeMs);
        MDC.remove("jobId");
        MDC.remove("jobType");
    }

    /**
     * Records worker job execution start.
     */
    public void recordJobStart(String jobId, String jobType) {
        MDC.put("jobId", jobId);
        MDC.put("jobType", jobType);
        Sentry.configureScope(scope -> {
            scope.setTag("queue.job_id", jobId);
            scope.setTag("queue.job_type", jobType);
        });
        log.info("job_start job_id={} job_type={}", jobId, jobType);
    }

    /**
     * Records worker job execution completion.
     */
    public void recordJobComplete(String jobId, String jobType, long durationMs) {
        Sentry.metrics().distribution("queue.job.duration_ms", (double) durationMs);
        Sentry.metrics().count("queue.job.success", 1.0);
        log.info("job_complete job_id={} job_type={} duration_ms={}", jobId, jobType, durationMs);
        MDC.remove("jobId");
        MDC.remove("jobType");
    }

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

        Sentry.metrics().count("queue.job.failure", 1.0);
        log.error("queue_failure job_id={} job_type={}", jobId, jobType, error);
        MDC.remove("jobId");
    }

    /**
     * Captures a dead-letter scenario — job exhausted all retries.
     */
    public void captureDeadLetter(String jobId, String jobType, int retryCount, Throwable error) {
        MDC.put("jobId", jobId);
        MDC.put("retryCount", String.valueOf(retryCount));

        Sentry.withScope(scope -> {
            scope.setTag("queue.job_id", jobId);
            scope.setTag("queue.job_type", jobType);
            scope.setExtra("retry.count", String.valueOf(retryCount));
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });

        Sentry.metrics().count("queue.dead_letter", 1.0);
        log.error("dead_letter job_id={} job_type={} retry_count={}", jobId, jobType, retryCount, error);
        MDC.remove("jobId");
        MDC.remove("retryCount");
    }

    /**
     * Records a retry attempt for any worker context.
     */
    public void recordRetry(String context, String contextId, int retryCount) {
        MDC.put("retryCount", String.valueOf(retryCount));
        Sentry.configureScope(scope -> {
            scope.setExtra("retry.context", context);
            scope.setExtra("retry.context_id", contextId);
            scope.setExtra("retry.count", String.valueOf(retryCount));
        });
        Sentry.metrics().count("worker.retry", 1.0);
        log.warn("retry_attempt context={} context_id={} retry_count={}",
                context, contextId, retryCount);
        MDC.remove("retryCount");
    }

    // -------------------------------------------------------------------------
    // GitHub webhook tracing
    // -------------------------------------------------------------------------

    /**
     * Records the start of processing a GitHub webhook event.
     */
    public void recordWebhookReceived(String eventType, String deliveryId) {
        MDC.put("githubEventId", deliveryId);
        MDC.put("githubEventType", eventType);
        Sentry.configureScope(scope -> {
            scope.setTag("github.event_type", eventType);
            scope.setTag("github.delivery_id", deliveryId);
        });
        Sentry.metrics().count("webhook.received", 1.0);
        log.info("webhook_received event_type={} delivery_id={}", eventType, deliveryId);
    }

    /**
     * Records successful processing of a GitHub webhook event.
     */
    public void recordWebhookProcessed(String eventType, String deliveryId, long durationMs) {
        Sentry.metrics().distribution("webhook.processing_ms", (double) durationMs);
        Sentry.metrics().count("webhook.processed", 1.0);
        log.info("webhook_processed event_type={} delivery_id={} duration_ms={}",
                eventType, deliveryId, durationMs);
        MDC.remove("githubEventId");
        MDC.remove("githubEventType");
    }

    /**
     * Captures a GitHub webhook processing failure.
     */
    public void captureWebhookProcessingFailure(String eventType, String deliveryId, Throwable error) {
        Sentry.withScope(scope -> {
            scope.setTag("github.event_type", eventType);
            scope.setTag("github.delivery_id", deliveryId);
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(error);
        });
        Sentry.metrics().count("webhook.failure", 1.0);
        log.error("webhook_processing_failure event_type={} delivery_id={}", eventType, deliveryId, error);
        MDC.remove("githubEventId");
        MDC.remove("githubEventType");
    }

    /**
     * Captures a GitHub webhook retry failure (all retries exhausted).
     */
    public void captureWebhookRetryFailure(String githubEventId, int retryCount, Throwable error) {
        MDC.put("githubEventId", githubEventId);
        MDC.put("retryCount", String.valueOf(retryCount));

        Sentry.withScope(scope -> {
            scope.setTag("github.event_id", githubEventId);
            scope.setExtra("retry.count", String.valueOf(retryCount));
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
    // GitHub integration — installation and repository
    // -------------------------------------------------------------------------

    /**
     * Records a GitHub installation sync event (registered, suspended, deleted).
     */
    public void recordInstallationSync(long installationId, String action) {
        MDC.put("githubInstallationId", String.valueOf(installationId));
        Sentry.configureScope(scope -> {
            scope.setTag("github.installation_id", String.valueOf(installationId));
            scope.setTag("github.installation_action", action);
        });
        Sentry.metrics().count("github.installation." + action, 1.0);
        log.info("installation_sync installation_id={} action={}", installationId, action);
        MDC.remove("githubInstallationId");
    }

    /**
     * Records a GitHub repository sync.
     */
    public void recordRepositorySync(String repoFullName, int prCount, long durationMs) {
        MDC.put("repoFullName", repoFullName);
        Sentry.metrics().distribution("github.repo_sync.duration_ms", (double) durationMs);
        Sentry.metrics().count("github.repo_sync", 1.0);
        log.info("repository_sync repo={} pr_count={} duration_ms={}", repoFullName, prCount, durationMs);
        MDC.remove("repoFullName");
    }

    /**
     * Records a branch creation event.
     */
    public void recordBranchCreation(String repoFullName, String branchName, String workflowId) {
        MDC.put("repoFullName", repoFullName);
        MDC.put("branchName", branchName);
        MDC.put("workflowId", workflowId);
        Sentry.configureScope(scope -> {
            scope.setTag("github.repo", repoFullName);
            scope.setTag("github.branch", branchName);
            scope.setTag("workflow.id", workflowId);
        });
        Sentry.metrics().count("github.branch.created", 1.0);
        log.info("branch_created repo={} branch={} workflow_id={}", repoFullName, branchName, workflowId);
        MDC.remove("repoFullName");
        MDC.remove("branchName");
        MDC.remove("workflowId");
    }

    /**
     * Records a PR creation event.
     */
    public void recordPRCreation(String repoFullName, int prNumber, String branchName, String workflowId) {
        MDC.put("repoFullName", repoFullName);
        MDC.put("prNumber", String.valueOf(prNumber));
        MDC.put("workflowId", workflowId);
        Sentry.configureScope(scope -> {
            scope.setTag("github.repo", repoFullName);
            scope.setTag("github.pr_number", String.valueOf(prNumber));
            scope.setTag("workflow.id", workflowId);
        });
        Sentry.metrics().count("github.pr.created", 1.0);
        log.info("pr_created repo={} pr_number={} branch={} workflow_id={}",
                repoFullName, prNumber, branchName, workflowId);
        MDC.remove("repoFullName");
        MDC.remove("prNumber");
        MDC.remove("workflowId");
    }

    /**
     * Records a GitHub API request/response for observability.
     */
    public void recordGitHubApiCall(String endpoint, int statusCode, long durationMs) {
        Sentry.metrics().distribution("github.api.duration_ms", (double) durationMs);
        if (statusCode >= 400) {
            Sentry.metrics().count("github.api.error", 1.0);
            log.warn("github_api_call endpoint={} status={} duration_ms={}", endpoint, statusCode, durationMs);
        } else {
            Sentry.metrics().count("github.api.success", 1.0);
            log.debug("github_api_call endpoint={} status={} duration_ms={}", endpoint, statusCode, durationMs);
        }
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
            scope.setExtra("timeout.duration_ms", String.valueOf(durationMs));
            scope.setLevel(SentryLevel.WARNING);
            Sentry.captureMessage("Timeout: " + operation + " [" + contextId + "]");
        });
        Sentry.metrics().distribution("worker.timeout_ms", (double) durationMs);
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
