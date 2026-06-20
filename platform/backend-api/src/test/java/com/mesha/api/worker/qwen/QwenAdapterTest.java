package com.mesha.api.worker.qwen;

import com.mesha.api.worker.observability.WorkflowTracer;
import com.mesha.api.worker.orchestration.SessionRequest;
import com.mesha.api.worker.orchestration.SessionResult.SessionStatus;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QwenAdapterTest {

    private QwenExecutionMonitor monitor;
    private QwenProperties properties;
    private QwenAdapter adapter;

    @BeforeEach
    void setUp() {
        monitor = new QwenExecutionMonitor(new WorkflowTracer(new SimpleTracer()));
        properties = new QwenProperties("qwen", List.of(), null, 30);
    }

    private SessionRequest minimalRequest() {
        return new SessionRequest("issue-1", "MES-1", "Fix bug", "Some details", "todo", "high",
                "assignee", List.of("bug"), "2024-01-01", "2024-01-02", "ws", "proj", "repo",
                "https://github.com/org/repo", "main", List.of(), "api-key", null, null, null, null, null, null);
    }

    @Test
    void providerName_returnsQwen() {
        adapter = new QwenAdapter((req, listener) -> new ProcessExecutionResult(0, "done", "", false),
                monitor, properties, new WorkflowTracer(new SimpleTracer()));
        assertThat(adapter.providerName()).isEqualTo("qwen");
    }

    @Test
    void createSession_returnsPendingResultWithGeneratedExecutionId() {
        CountDownLatch release = new CountDownLatch(1);
        adapter = new QwenAdapter((req, listener) -> {
            await(release);
            return new ProcessExecutionResult(0, "done", "", false);
        }, monitor, properties, new WorkflowTracer(new SimpleTracer()));

        var result = adapter.createSession(minimalRequest());

        assertThat(result.status()).isEqualTo(SessionStatus.PENDING);
        assertThat(result.providerSessionId()).isNotBlank();
        release.countDown();
    }

    @Test
    void pollSession_returnsRunningWhileProcessExecutes() {
        CountDownLatch release = new CountDownLatch(1);
        adapter = new QwenAdapter((req, listener) -> {
            await(release);
            return new ProcessExecutionResult(0, "done", "", false);
        }, monitor, properties, new WorkflowTracer(new SimpleTracer()));

        var created = adapter.createSession(minimalRequest());
        var polled = adapter.pollSession(created.providerSessionId());

        assertThat(polled.status()).isEqualTo(SessionStatus.RUNNING);
        release.countDown();
    }

    @Test
    void pollSession_returnsCompletedAfterSuccessfulExit() throws InterruptedException {
        adapter = new QwenAdapter((req, listener) -> new ProcessExecutionResult(0, "final output", "", false),
                monitor, properties, new WorkflowTracer(new SimpleTracer()));

        var created = adapter.createSession(minimalRequest());
        var polled = awaitTerminalStatus(created.providerSessionId());

        assertThat(polled.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(polled.finalMessage()).isEqualTo("final output");
    }

    @Test
    void pollSession_returnsFailedWithStderrTailOnNonZeroExit() throws InterruptedException {
        adapter = new QwenAdapter((req, listener) -> {
            listener.onStderr(req.executionId(), "boom");
            return new ProcessExecutionResult(1, "", "boom", false);
        }, monitor, properties, new WorkflowTracer(new SimpleTracer()));

        var created = adapter.createSession(minimalRequest());
        var polled = awaitTerminalStatus(created.providerSessionId());

        assertThat(polled.status()).isEqualTo(SessionStatus.FAILED);
        assertThat(polled.finalMessage()).contains("exited with code 1").contains("boom");
    }

    @Test
    void pollSession_returnsFailedOnTimeout() throws InterruptedException {
        adapter = new QwenAdapter((req, listener) -> new ProcessExecutionResult(-1, "", "", true),
                monitor, properties, new WorkflowTracer(new SimpleTracer()));

        var created = adapter.createSession(minimalRequest());
        var polled = awaitTerminalStatus(created.providerSessionId());

        assertThat(polled.status()).isEqualTo(SessionStatus.FAILED);
        assertThat(polled.finalMessage()).contains("timed out");
    }

    @Test
    void pollSession_returnsFailedWhenProcessExecutorThrows() throws InterruptedException {
        adapter = new QwenAdapter((req, listener) -> {
            throw new ProcessExecutionException("could not start", new java.io.IOException("no such file"));
        }, monitor, properties, new WorkflowTracer(new SimpleTracer()));

        var created = adapter.createSession(minimalRequest());
        var polled = awaitTerminalStatus(created.providerSessionId());

        assertThat(polled.status()).isEqualTo(SessionStatus.FAILED);
        assertThat(polled.finalMessage()).contains("could not start");
    }

    @Test
    void pollSession_throwsForUnknownExecutionId() {
        adapter = new QwenAdapter((req, listener) -> new ProcessExecutionResult(0, "", "", false),
                monitor, properties, new WorkflowTracer(new SimpleTracer()));

        assertThatThrownBy(() -> adapter.pollSession("unknown-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelSession_cancelsRunningFutureAndDisposesState() {
        CountDownLatch release = new CountDownLatch(1);
        adapter = new QwenAdapter((req, listener) -> {
            await(release);
            return new ProcessExecutionResult(0, "done", "", false);
        }, monitor, properties, new WorkflowTracer(new SimpleTracer()));

        var created = adapter.createSession(minimalRequest());
        adapter.cancelSession(created.providerSessionId());

        assertThatThrownBy(() -> adapter.pollSession(created.providerSessionId()))
                .isInstanceOf(IllegalArgumentException.class);
        release.countDown();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private com.mesha.api.worker.orchestration.SessionResult awaitTerminalStatus(String executionId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        com.mesha.api.worker.orchestration.SessionResult result;
        do {
            result = adapter.pollSession(executionId);
            if (result.status() == SessionStatus.COMPLETED || result.status() == SessionStatus.FAILED) {
                return result;
            }
            Thread.sleep(20);
        } while (System.currentTimeMillis() < deadline);
        return result;
    }
}
