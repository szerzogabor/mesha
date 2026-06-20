package com.mesha.api.worker.qwen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs commands via {@link ProcessBuilder}, draining stdout/stderr on dedicated
 * threads so neither pipe can block the process while it is running, and
 * enforcing the requested timeout by force-killing the process if it is exceeded.
 */
@Component
public class DefaultProcessExecutor implements ProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessExecutor.class);

    @Override
    public ProcessExecutionResult execute(ProcessExecutionRequest request, ProcessExecutionListener listener) {
        String executionId = request.executionId() != null ? request.executionId() : UUID.randomUUID().toString();
        ProcessExecutionListener safeListener = listener != null ? listener : new ProcessExecutionListener() {};
        ExecutorService streamReaders = Executors.newFixedThreadPool(2);

        try {
            ProcessBuilder builder = new ProcessBuilder(request.command());
            if (request.workingDirectory() != null) {
                builder.directory(request.workingDirectory().toFile());
            }
            if (request.environment() != null) {
                builder.environment().putAll(request.environment());
            }

            safeListener.onStart(executionId, request.command());
            Process process = builder.start();

            if (request.stdin() != null) {
                writeStdin(process.getOutputStream(), request.stdin());
            } else {
                closeQuietly(process.getOutputStream());
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Future<?> stdoutFuture = streamReaders.submit(() ->
                    drainStream(process.getInputStream(), stdout, line -> safeListener.onStdout(executionId, line)));
            Future<?> stderrFuture = streamReaders.submit(() ->
                    drainStream(process.getErrorStream(), stderr, line -> safeListener.onStderr(executionId, line)));

            boolean finished = request.timeout() != null
                    ? process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS)
                    : awaitIndefinitely(process);

            if (!finished) {
                process.destroyForcibly();
                awaitQuietly(stdoutFuture);
                awaitQuietly(stderrFuture);
                log.warn("qwen_process_timeout execution_id={} command={}", executionId, request.command());
                return new ProcessExecutionResult(-1, stdout.toString(), stderr.toString(), true);
            }

            awaitQuietly(stdoutFuture);
            awaitQuietly(stderrFuture);

            int exitCode = process.exitValue();
            safeListener.onExit(executionId, exitCode);
            return new ProcessExecutionResult(exitCode, stdout.toString(), stderr.toString(), false);

        } catch (IOException e) {
            safeListener.onFailure(executionId, e);
            throw new ProcessExecutionException("Failed to start process: " + request.command(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            safeListener.onFailure(executionId, e);
            throw new ProcessExecutionException("Process execution interrupted: " + request.command(), e);
        } finally {
            streamReaders.shutdownNow();
        }
    }

    private boolean awaitIndefinitely(Process process) throws InterruptedException {
        process.waitFor();
        return true;
    }

    private void writeStdin(OutputStream stdin, String content) {
        try (stdin) {
            stdin.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("qwen_stdin_write_failed error={}", e.getMessage());
        }
    }

    private void closeQuietly(OutputStream stdin) {
        try {
            stdin.close();
        } catch (IOException e) {
            log.debug("qwen_stdin_close_failed error={}", e.getMessage());
        }
    }

    private void drainStream(InputStream input, StringBuilder buffer, Consumer<String> onLine) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append('\n');
                onLine.accept(line);
            }
        } catch (IOException e) {
            log.debug("qwen_stream_read_interrupted error={}", e.getMessage());
        }
    }

    private void awaitQuietly(Future<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("qwen_stream_drain_incomplete error={}", e.getMessage());
        }
    }
}
