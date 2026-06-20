package com.mesha.api.worker.qwen;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultProcessExecutorTest {

    private final DefaultProcessExecutor executor = new DefaultProcessExecutor();

    @Test
    void execute_capturesStdoutAndSuccessExitCode() {
        var request = new ProcessExecutionRequest(
                "exec-1", List.of("sh", "-c", "echo hello-out"), null, null, null, Duration.ofSeconds(5));

        ProcessExecutionResult result = executor.execute(request, null);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("hello-out");
        assertThat(result.timedOut()).isFalse();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_capturesStderrAndNonZeroExitCode() {
        var request = new ProcessExecutionRequest(
                "exec-2", List.of("sh", "-c", "echo boom 1>&2; exit 3"), null, null, null, Duration.ofSeconds(5));

        ProcessExecutionResult result = executor.execute(request, null);

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.stderr()).contains("boom");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_writesStdinToProcess() {
        var request = new ProcessExecutionRequest(
                "exec-3", List.of("cat"), null, null, "piped-input", Duration.ofSeconds(5));

        ProcessExecutionResult result = executor.execute(request, null);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("piped-input");
    }

    @Test
    void execute_killsProcessOnTimeout() {
        var request = new ProcessExecutionRequest(
                "exec-4", List.of("sh", "-c", "sleep 5"), null, null, null, Duration.ofMillis(100));

        ProcessExecutionResult result = executor.execute(request, null);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_throwsProcessExecutionExceptionWhenCommandMissing() {
        var request = new ProcessExecutionRequest(
                "exec-5", List.of("definitely-not-a-real-command-xyz"), null, null, null, Duration.ofSeconds(5));

        assertThatThrownBy(() -> executor.execute(request, null))
                .isInstanceOf(ProcessExecutionException.class);
    }

    @Test
    void execute_invokesListenerLifecycleAndStreamCallbacksWithRequestExecutionId() {
        List<String> events = new CopyOnWriteArrayList<>();
        ProcessExecutionListener listener = new ProcessExecutionListener() {
            @Override
            public void onStart(String executionId, List<String> command) {
                events.add("start:" + executionId);
            }

            @Override
            public void onStdout(String executionId, String line) {
                events.add("stdout:" + executionId + ":" + line);
            }

            @Override
            public void onExit(String executionId, int exitCode) {
                events.add("exit:" + executionId + ":" + exitCode);
            }
        };

        var request = new ProcessExecutionRequest(
                "exec-6", List.of("sh", "-c", "echo line-one"), null, null, null, Duration.ofSeconds(5));

        executor.execute(request, listener);

        assertThat(events).containsExactly("start:exec-6", "stdout:exec-6:line-one", "exit:exec-6:0");
    }
}
