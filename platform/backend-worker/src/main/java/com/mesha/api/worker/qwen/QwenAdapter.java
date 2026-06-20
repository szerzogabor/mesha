package com.mesha.api.worker.qwen;

import com.mesha.api.worker.observability.WorkflowTracer;
import com.mesha.api.worker.orchestration.ProviderAdapter;
import com.mesha.api.worker.orchestration.SessionRequest;
import com.mesha.api.worker.orchestration.SessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provider adapter that runs the Qwen CLI as a local subprocess instead of
 * calling a remote API. Because the CLI call is synchronous and potentially
 * long-running, {@link #createSession} only starts the process (on a worker
 * thread) and {@link #pollSession} reports on its progress, mirroring the
 * create/poll contract every other {@link ProviderAdapter} follows.
 */
@Component
public class QwenAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(QwenAdapter.class);
    private static final int STDERR_TAIL_LINES = 20;

    private final ProcessExecutor processExecutor;
    private final QwenExecutionMonitor monitor;
    private final QwenProperties properties;
    private final WorkflowTracer workflowTracer;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, RunningExecution> running = new ConcurrentHashMap<>();

    public QwenAdapter(ProcessExecutor processExecutor, QwenExecutionMonitor monitor,
                        QwenProperties properties, WorkflowTracer workflowTracer) {
        this.processExecutor = processExecutor;
        this.monitor = monitor;
        this.properties = properties;
        this.workflowTracer = workflowTracer;
    }

    @Override
    public String providerName() {
        return "qwen";
    }

    @Override
    public SessionResult createSession(SessionRequest request) {
        String executionId = UUID.randomUUID().toString();
        MDC.put("sessionId", executionId);
        MDC.put("provider", providerName());

        try {
            ProcessExecutionRequest execRequest = buildExecutionRequest(executionId, request);
            monitor.register(executionId);

            log.info("qwen_session_create_start provider={} issue_id={} execution_id={} command={}",
                    providerName(), request.issueId(), executionId, execRequest.command());

            Future<ProcessExecutionResult> future =
                    executorService.submit(() -> processExecutor.execute(execRequest, monitor));
            running.put(executionId, new RunningExecution(future, new AtomicInteger(0)));

            return new SessionResult(executionId, SessionResult.SessionStatus.PENDING, null, null, null, null);
        } catch (Exception e) {
            workflowTracer.captureAiProviderFailure(providerName(), "createSession", 0, e);
            throw new RuntimeException("Failed to start Qwen session: " + e.getMessage(), e);
        } finally {
            MDC.remove("sessionId");
            MDC.remove("provider");
        }
    }

    @Override
    public SessionResult pollSession(String providerSessionId) {
        RunningExecution execution = running.get(providerSessionId);
        if (execution == null) {
            throw new IllegalArgumentException("Unknown Qwen execution: " + providerSessionId);
        }

        List<String> newMessages = monitor.drainNewStdout(providerSessionId, execution.offset());
        List<String> messages = newMessages.isEmpty() ? null : newMessages;

        if (!execution.future().isDone()) {
            return new SessionResult(providerSessionId, SessionResult.SessionStatus.RUNNING, null, null, null, messages);
        }

        try {
            ProcessExecutionResult result = execution.future().get();
            return finish(providerSessionId, messages, mapResult(providerSessionId, result));
        } catch (ExecutionException e) {
            workflowTracer.capturePollingFailure(providerName(), providerSessionId, 1, e.getCause());
            return finish(providerSessionId, messages, new SessionResult(providerSessionId,
                    SessionResult.SessionStatus.FAILED,
                    "Qwen process execution failed: " + e.getCause().getMessage(), null, null, null));
        } catch (Exception e) {
            workflowTracer.capturePollingFailure(providerName(), providerSessionId, 1, e);
            return finish(providerSessionId, messages, new SessionResult(providerSessionId,
                    SessionResult.SessionStatus.FAILED,
                    "Qwen process execution failed: " + e.getMessage(), null, null, null));
        }
    }

    @Override
    public void cancelSession(String providerSessionId) {
        RunningExecution execution = running.remove(providerSessionId);
        if (execution != null) {
            execution.future().cancel(true);
        }
        monitor.dispose(providerSessionId);
        log.info("qwen_session_cancel provider={} execution_id={}", providerName(), providerSessionId);
    }

    private SessionResult mapResult(String executionId, ProcessExecutionResult result) {
        if (result.timedOut()) {
            log.warn("qwen_session_timeout provider={} execution_id={}", providerName(), executionId);
            return new SessionResult(executionId, SessionResult.SessionStatus.FAILED,
                    "Qwen process timed out after " + properties.timeoutSeconds() + "s", null, null, null);
        }
        if (result.exitCode() != 0) {
            String stderrTail = monitor.stderrTail(executionId, STDERR_TAIL_LINES);
            String message = "Qwen process exited with code " + result.exitCode()
                    + (stderrTail.isBlank() ? "" : ": " + stderrTail);
            log.warn("qwen_session_failed provider={} execution_id={} exit_code={}",
                    providerName(), executionId, result.exitCode());
            return new SessionResult(executionId, SessionResult.SessionStatus.FAILED, message, null, null, null);
        }
        log.info("qwen_session_completed provider={} execution_id={}", providerName(), executionId);
        return new SessionResult(executionId, SessionResult.SessionStatus.COMPLETED, result.stdout(), null, null, null);
    }

    private SessionResult finish(String executionId, List<String> trailingMessages, SessionResult outcome) {
        running.remove(executionId);
        monitor.dispose(executionId);
        if (trailingMessages == null) {
            return outcome;
        }
        return new SessionResult(outcome.providerSessionId(), outcome.status(), outcome.finalMessage(),
                outcome.workspaceId(), outcome.sessionHtmlUrl(), trailingMessages);
    }

    private ProcessExecutionRequest buildExecutionRequest(String executionId, SessionRequest request) {
        List<String> command = new ArrayList<>();
        command.add(properties.cliPath());
        command.addAll(properties.extraArgs());

        Path workingDirectory = (properties.workingDirectory() != null && !properties.workingDirectory().isBlank())
                ? Path.of(properties.workingDirectory())
                : null;

        return new ProcessExecutionRequest(
                executionId,
                command,
                workingDirectory,
                Map.of(),
                buildPrompt(request),
                Duration.ofSeconds(properties.timeoutSeconds()));
    }

    private String buildPrompt(SessionRequest request) {
        StringBuilder sb = new StringBuilder();

        if (request.agentSystemPrompt() != null && !request.agentSystemPrompt().isBlank()) {
            sb.append(request.agentSystemPrompt().trim()).append("\n\n");
        }

        appendField(sb, "Issue", request.issueIdentifier());
        appendField(sb, "Title", request.issueTitle());
        if (request.issueDescription() != null && !request.issueDescription().isBlank()) {
            sb.append("\nDescription\n\n").append(request.issueDescription()).append('\n');
        }
        appendField(sb, "Repository", request.repositoryUrl());

        List<String> comments = request.comments();
        if (comments != null && !comments.isEmpty()) {
            sb.append("\nComments\n\n");
            for (String comment : comments) {
                sb.append(comment).append("\n\n");
            }
        }

        if (request.instructions() != null && !request.instructions().isBlank()) {
            sb.append("\nAdditional Instructions\n\n").append(request.instructions()).append('\n');
        }

        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private record RunningExecution(Future<ProcessExecutionResult> future, AtomicInteger offset) {}
}
