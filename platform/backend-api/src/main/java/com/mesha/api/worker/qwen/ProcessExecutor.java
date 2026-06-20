package com.mesha.api.worker.qwen;

/**
 * Abstraction over launching an external CLI process so adapters never call
 * {@link ProcessBuilder} directly. Implementations decide how the process is
 * started, how long to wait for it, and how output is captured.
 */
public interface ProcessExecutor {

    ProcessExecutionResult execute(ProcessExecutionRequest request, ProcessExecutionListener listener);
}
