package com.mesha.worker.observability;

import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowTracerTest {

    private SimpleTracer simpleTracer;
    private WorkflowTracer workflowTracer;

    @BeforeEach
    void setUp() {
        simpleTracer = new SimpleTracer();
        workflowTracer = new WorkflowTracer(simpleTracer);
    }

    @AfterEach
    void cleanUpMdc() {
        MDC.clear();
    }

    // ---- traceOperation ----

    @Test
    void traceOperation_returnsResultOnSuccess() throws Exception {
        String result = workflowTracer.traceOperation("test-span", "test-op",
                Map.of("env", "test"), () -> "expected");
        assertThat(result).isEqualTo("expected");
    }

    @Test
    void traceOperation_rethrowsRuntimeException() {
        assertThatThrownBy(() -> workflowTracer.traceOperation("test-span", "test-op",
                Map.of(), () -> { throw new IllegalStateException("boom"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void traceOperation_wrapsCheckedExceptionInRuntimeException() {
        assertThatThrownBy(() -> workflowTracer.traceOperation("test-span", "test-op",
                Map.of(), () -> { throw new Exception("checked"); }))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void traceOperation_clearsMdcTraceIdAfterCompletion() throws Exception {
        workflowTracer.traceOperation("test-span", "test-op", Map.of(), () -> "ok");
        assertThat(MDC.get("traceId")).isNull();
    }

    // ---- Orchestration ----

    @Test
    void recordOrchestrationTransition_setsMdcKeys() {
        workflowTracer.recordOrchestrationTransition("wf-1", "PENDING", "RUNNING");
        assertThat(MDC.get("workflowId")).isEqualTo("wf-1");
    }

    // ---- GitHub PR workflow ----

    @Test
    void recordGitHubPrWorkflowStart_setsMdcKeys() {
        workflowTracer.recordGitHubPrWorkflowStart("inst-123", "org/repo", "42", "pull_request");
        assertThat(MDC.get("installationId")).isEqualTo("inst-123");
        assertThat(MDC.get("githubRepository")).isEqualTo("org/repo");
        assertThat(MDC.get("prNumber")).isEqualTo("42");
    }

    @Test
    void recordGitHubPrWorkflowComplete_clearsMdcKeys() {
        workflowTracer.recordGitHubPrWorkflowStart("inst-123", "org/repo", "42", "pull_request");
        workflowTracer.recordGitHubPrWorkflowComplete("inst-123", "org/repo", "42", "SUCCESS", 100L);
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
        assertThat(MDC.get("prNumber")).isNull();
    }

    @Test
    void captureGitHubPrWorkflowFailure_setsThenClearsMdcKeys() {
        workflowTracer.captureGitHubPrWorkflowFailure("inst-123", "org/repo", "42",
                new RuntimeException("pr failed"));
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
        assertThat(MDC.get("prNumber")).isNull();
    }

    // ---- Repository sync ----

    @Test
    void recordRepositorySyncStart_setsMdcKeys() {
        workflowTracer.recordRepositorySyncStart("inst-456", "org/repo2", "push");
        assertThat(MDC.get("installationId")).isEqualTo("inst-456");
        assertThat(MDC.get("githubRepository")).isEqualTo("org/repo2");
    }

    @Test
    void recordRepositorySyncComplete_clearsMdcKeys() {
        workflowTracer.recordRepositorySyncStart("inst-456", "org/repo2", "push");
        workflowTracer.recordRepositorySyncComplete("inst-456", "org/repo2", 200L);
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
    }

    @Test
    void captureRepositorySyncFailure_clearsMdcKeys() {
        workflowTracer.captureRepositorySyncFailure("inst-456", "org/repo2",
                new RuntimeException("sync failed"));
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
    }

    // ---- Webhook-triggered workflow ----

    @Test
    void recordWebhookWorkflowTriggered_setsMdcKeys() {
        workflowTracer.recordWebhookWorkflowTriggered("evt-789", "push", "inst-789", "org/repo3");
        assertThat(MDC.get("githubEventId")).isEqualTo("evt-789");
        assertThat(MDC.get("installationId")).isEqualTo("inst-789");
        assertThat(MDC.get("githubRepository")).isEqualTo("org/repo3");
    }

    @Test
    void recordWebhookWorkflowComplete_clearsMdcKeys() {
        workflowTracer.recordWebhookWorkflowTriggered("evt-789", "push", "inst-789", "org/repo3");
        workflowTracer.recordWebhookWorkflowComplete("evt-789", "SUCCESS", 150L);
        assertThat(MDC.get("githubEventId")).isNull();
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
    }

    // ---- Retry ----

    @Test
    void recordRetry_clearsMdcAfterCall() {
        workflowTracer.recordRetry("queue", "job-1", 3);
        assertThat(MDC.get("retryCount")).isNull();
    }

    // ---- Timeout / cancellation ----

    @Test
    void captureTimeoutEvent_clearsMdcAfterCall() {
        workflowTracer.captureTimeoutEvent("orchestration", "wf-2", 5000L);
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void recordCancellation_clearsMdcAfterCall() {
        workflowTracer.recordCancellation("wf-3", "user_requested");
        assertThat(MDC.get("workflowId")).isNull();
    }
}
