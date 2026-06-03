package com.mesha.api.observability;

import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiTracerTest {

    private SimpleTracer simpleTracer;
    private ApiTracer apiTracer;

    @BeforeEach
    void setUp() {
        simpleTracer = new SimpleTracer();
        apiTracer = new ApiTracer(simpleTracer);
    }

    @AfterEach
    void cleanUpMdc() {
        MDC.clear();
    }

    // ---- traceOperation ----

    @Test
    void traceOperation_returnsResultOnSuccess() throws Exception {
        String result = apiTracer.traceOperation("test-span", "test-op",
                Map.of("env", "test"), () -> "expected");
        assertThat(result).isEqualTo("expected");
    }

    @Test
    void traceOperation_rethrowsRuntimeException() {
        assertThatThrownBy(() -> apiTracer.traceOperation("test-span", "test-op",
                Map.of(), () -> { throw new IllegalStateException("boom"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void traceOperation_wrapsCheckedExceptionInRuntimeException() {
        assertThatThrownBy(() -> apiTracer.traceOperation("test-span", "test-op",
                Map.of(), () -> { throw new Exception("checked"); }))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void traceOperation_clearsMdcTraceIdAfterCompletion() throws Exception {
        apiTracer.traceOperation("test-span", "test-op", Map.of(), () -> "ok");
        assertThat(MDC.get("traceId")).isNull();
    }

    // ---- GitHub webhook ----

    @Test
    void recordGitHubWebhookReceived_setsMdcKeys() {
        apiTracer.recordGitHubWebhookReceived("evt-123", "push", "inst-456", "org/repo");
        assertThat(MDC.get("githubEventId")).isEqualTo("evt-123");
        assertThat(MDC.get("installationId")).isEqualTo("inst-456");
        assertThat(MDC.get("githubRepository")).isEqualTo("org/repo");
    }

    @Test
    void recordGitHubWebhookProcessed_clearsMdcKeys() {
        apiTracer.recordGitHubWebhookReceived("evt-123", "push", "inst-456", "org/repo");
        apiTracer.recordGitHubWebhookProcessed("evt-123", "SUCCESS", 100L);
        assertThat(MDC.get("githubEventId")).isNull();
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
    }

    @Test
    void captureGitHubWebhookFailure_clearsMdcKeys() {
        apiTracer.captureGitHubWebhookFailure("evt-123", "push", new RuntimeException("fail"));
        assertThat(MDC.get("githubEventId")).isNull();
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
    }

    // ---- GitHub PR workflow ----

    @Test
    void recordGitHubPrWorkflowStart_setsMdcKeys() {
        apiTracer.recordGitHubPrWorkflowStart("inst-123", "org/repo", "42", "pull_request");
        assertThat(MDC.get("installationId")).isEqualTo("inst-123");
        assertThat(MDC.get("githubRepository")).isEqualTo("org/repo");
        assertThat(MDC.get("prNumber")).isEqualTo("42");
    }

    @Test
    void recordGitHubPrWorkflowComplete_clearsMdcKeys() {
        apiTracer.recordGitHubPrWorkflowStart("inst-123", "org/repo", "42", "pull_request");
        apiTracer.recordGitHubPrWorkflowComplete("inst-123", "org/repo", "42", "SUCCESS", 100L);
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
        assertThat(MDC.get("prNumber")).isNull();
    }

    @Test
    void captureGitHubPrWorkflowFailure_clearsMdcKeys() {
        apiTracer.captureGitHubPrWorkflowFailure("inst-123", "org/repo", "42",
                new RuntimeException("pr failed"));
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
        assertThat(MDC.get("prNumber")).isNull();
    }

    // ---- Repository sync ----

    @Test
    void recordRepositorySyncStart_setsMdcKeys() {
        apiTracer.recordRepositorySyncStart("inst-456", "org/repo2", "push");
        assertThat(MDC.get("installationId")).isEqualTo("inst-456");
        assertThat(MDC.get("githubRepository")).isEqualTo("org/repo2");
    }

    @Test
    void recordRepositorySyncComplete_clearsMdcKeys() {
        apiTracer.recordRepositorySyncStart("inst-456", "org/repo2", "push");
        apiTracer.recordRepositorySyncComplete("inst-456", "org/repo2", 200L);
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
    }

    @Test
    void captureRepositorySyncFailure_clearsMdcKeys() {
        apiTracer.captureRepositorySyncFailure("inst-456", "org/repo2",
                new RuntimeException("sync failed"));
        assertThat(MDC.get("installationId")).isNull();
        assertThat(MDC.get("githubRepository")).isNull();
    }

    // ---- AI draft workflows ----

    @Test
    void recordAIDraftStarted_setsMdcKeys() {
        apiTracer.recordAIDraftStarted("draft-1", "claude", "issue-99");
        assertThat(MDC.get("draftId")).isEqualTo("draft-1");
        assertThat(MDC.get("aiProvider")).isEqualTo("claude");
    }

    @Test
    void recordAIDraftCompleted_clearsMdcKeys() {
        apiTracer.recordAIDraftStarted("draft-1", "claude", "issue-99");
        apiTracer.recordAIDraftCompleted("draft-1", "claude", 300L);
        assertThat(MDC.get("draftId")).isNull();
        assertThat(MDC.get("aiProvider")).isNull();
    }

    @Test
    void captureAIDraftFailure_clearsMdcKeys() {
        apiTracer.captureAIDraftFailure("draft-2", "claude", new RuntimeException("ai error"));
        assertThat(MDC.get("draftId")).isNull();
        assertThat(MDC.get("aiProvider")).isNull();
    }

    // ---- Auth flows ----

    @Test
    void captureAuthFailure_clearsMdcAfterCall() {
        apiTracer.captureAuthFailure("expired_token", "/api/issues", new RuntimeException("expired"));
        assertThat(MDC.get("traceId")).isNull();
    }
}
