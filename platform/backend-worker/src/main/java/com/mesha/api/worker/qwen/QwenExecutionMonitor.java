package com.mesha.api.worker.qwen;

import com.mesha.api.worker.observability.WorkflowTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures stdout/stderr and lifecycle events for in-flight Qwen process
 * executions. Lifecycle events are emitted as structured logs (shipped to
 * Loki, same as every other AI provider event in this codebase) so they are
 * persisted without a dedicated execution-events table.
 */
@Component
public class QwenExecutionMonitor implements ProcessExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(QwenExecutionMonitor.class);

    private final WorkflowTracer workflowTracer;
    private final Map<String, Execution> executions = new ConcurrentHashMap<>();

    public QwenExecutionMonitor(WorkflowTracer workflowTracer) {
        this.workflowTracer = workflowTracer;
    }

    public void register(String executionId) {
        executions.put(executionId, new Execution());
    }

    @Override
    public void onStart(String executionId, List<String> command) {
        log.info("qwen_process_started execution_id={} command={}", executionId, command);
    }

    @Override
    public void onStdout(String executionId, String line) {
        track(executionId).stdoutLines.add(line);
        log.debug("qwen_process_stdout execution_id={} line={}", executionId, line);
    }

    @Override
    public void onStderr(String executionId, String line) {
        track(executionId).stderrLines.add(line);
        log.debug("qwen_process_stderr execution_id={} line={}", executionId, line);
    }

    @Override
    public void onExit(String executionId, int exitCode) {
        log.info("qwen_process_exited execution_id={} exit_code={}", executionId, exitCode);
    }

    @Override
    public void onFailure(String executionId, Throwable error) {
        workflowTracer.captureAiProviderFailure("qwen", "execute", 0, error);
    }

    /**
     * Returns stdout lines captured since the last call for this execution
     * (tracked via {@code offset}), mirroring the incremental-message pattern
     * used for polling other providers.
     */
    public List<String> drainNewStdout(String executionId, AtomicInteger offset) {
        Execution execution = executions.get(executionId);
        if (execution == null) {
            return List.of();
        }
        List<String> lines = execution.stdoutLines;
        int from = offset.get();
        if (from >= lines.size()) {
            return List.of();
        }
        List<String> newLines = List.copyOf(lines.subList(from, lines.size()));
        offset.set(lines.size());
        return newLines;
    }

    public String stderrTail(String executionId, int maxLines) {
        Execution execution = executions.get(executionId);
        if (execution == null || execution.stderrLines.isEmpty()) {
            return "";
        }
        List<String> lines = execution.stderrLines;
        int from = Math.max(0, lines.size() - maxLines);
        return String.join("\n", lines.subList(from, lines.size()));
    }

    public void dispose(String executionId) {
        executions.remove(executionId);
    }

    private Execution track(String executionId) {
        return executions.computeIfAbsent(executionId, id -> new Execution());
    }

    private static final class Execution {
        final List<String> stdoutLines = new CopyOnWriteArrayList<>();
        final List<String> stderrLines = new CopyOnWriteArrayList<>();
    }
}
