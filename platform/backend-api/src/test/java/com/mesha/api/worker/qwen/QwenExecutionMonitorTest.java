package com.mesha.api.worker.qwen;

import com.mesha.api.worker.observability.WorkflowTracer;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class QwenExecutionMonitorTest {

    private QwenExecutionMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new QwenExecutionMonitor(new WorkflowTracer(new SimpleTracer()));
    }

    @Test
    void drainNewStdout_returnsOnlyLinesSinceLastOffset() {
        String executionId = "exec-1";
        monitor.register(executionId);
        monitor.onStdout(executionId, "line-1");
        monitor.onStdout(executionId, "line-2");

        AtomicInteger offset = new AtomicInteger(0);
        List<String> firstBatch = monitor.drainNewStdout(executionId, offset);
        assertThat(firstBatch).containsExactly("line-1", "line-2");

        monitor.onStdout(executionId, "line-3");
        List<String> secondBatch = monitor.drainNewStdout(executionId, offset);
        assertThat(secondBatch).containsExactly("line-3");
    }

    @Test
    void drainNewStdout_returnsEmptyForUnknownExecution() {
        AtomicInteger offset = new AtomicInteger(0);
        assertThat(monitor.drainNewStdout("missing", offset)).isEmpty();
    }

    @Test
    void stderrTail_returnsLastNLines() {
        String executionId = "exec-2";
        monitor.register(executionId);
        for (int i = 1; i <= 5; i++) {
            monitor.onStderr(executionId, "err-" + i);
        }

        String tail = monitor.stderrTail(executionId, 2);

        assertThat(tail).isEqualTo("err-4\nerr-5");
    }

    @Test
    void stderrTail_returnsEmptyStringWhenNoStderrCaptured() {
        String executionId = "exec-3";
        monitor.register(executionId);

        assertThat(monitor.stderrTail(executionId, 5)).isEmpty();
    }

    @Test
    void dispose_removesTrackedExecution() {
        String executionId = "exec-4";
        monitor.register(executionId);
        monitor.onStdout(executionId, "line-1");

        monitor.dispose(executionId);

        AtomicInteger offset = new AtomicInteger(0);
        assertThat(monitor.drainNewStdout(executionId, offset)).isEmpty();
    }
}
