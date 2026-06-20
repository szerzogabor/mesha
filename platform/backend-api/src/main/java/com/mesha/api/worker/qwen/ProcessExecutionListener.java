package com.mesha.api.worker.qwen;

import java.util.List;

/**
 * Receives lifecycle and output events while a {@link ProcessExecutor} runs a
 * command, so callers can monitor a long-running process without blocking on
 * its final result.
 */
public interface ProcessExecutionListener {

    default void onStart(String executionId, List<String> command) {}

    default void onStdout(String executionId, String line) {}

    default void onStderr(String executionId, String line) {}

    default void onExit(String executionId, int exitCode) {}

    default void onFailure(String executionId, Throwable error) {}
}
