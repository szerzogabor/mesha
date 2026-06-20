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

    private final ExecutorService streamReaders = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("qwen-stream-reader");
        return t;
    });

    @Override
    public ProcessExecutionResult execute(ProcessExecutionRequest request, ProcessExecutionListener listener) {
        String executionId = request.executionId() != null ? request.executionId() : UUID.randomUUID().toString();
        ProcessExecutionListener safeListener = listener != null ? listener : new ProcessExecutionListener() {};
        Process process = null;

        try {
            ProcessBuilder builder = new ProcessBuilder(request.command());
            if (request.workingDirectory() != null) {
                builder.directory(request.workingDirectory().toFile());
            }
            if (request.environment() != null) {
                builder.environment().putAll(request.environment());
            }

            safeListener.onStart(executionId, request.command());
            process = builder.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            final Process finalProcess = process;

            // Start the stream readers before writing stdin, so a process that
            // writes to stdout/stderr before consuming all of stdin can't deadlock.
            Future<?> stdoutFuture = streamReaders.submit(() ->
                    drainStream(finalProcess.getInputStream(), stdout, line -> safeListener.onStdout(executionId, line)));
            Future<?> stderrFuture = streamReaders.submit(() ->
                    drainStream(finalProcess.getErrorStream(), stderr, line -> safeListener.onStderr(executionId, line)));

            Future<?> stdinFuture = null;
            if (request.stdin() != null) {
                stdinFuture = streamReaders.submit(() -> writeStdin(finalProcess.getOutputStream(), request.stdin()));
            } else {
                closeQuietly(process.getOutputStream());
            }

            boolean finished = request.timeout() != null
                    ? process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS)
                    : awaitIndefinitely(process);

            if (!finished) {
                process.destroyForcibly();
                awaitQuietly(stdoutFuture);
                awaitQuietly(stderrFuture);
                awaitQuietly(stdinFuture);
                log.warn("qwen_process_timeout execution_id={} command={}", executionId, request.command());
                return new ProcessExecutionResult(-1, stdout.toString(), stderr.toString(), true);
            }

            awaitQuietly(stdoutFuture);
            awaitQuietly(stderrFuture);
            awaitQuietly(stdinFuture);

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
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
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
        if (future == null) {
            return;
        }
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("qwen_stream_drain_interrupted", e);
        } catch (Exception e) {
            log.debug("qwen_stream_drain_incomplete error={}", e.getMessage());
        }
    }
}
