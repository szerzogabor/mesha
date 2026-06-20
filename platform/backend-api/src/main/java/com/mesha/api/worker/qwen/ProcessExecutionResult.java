package com.mesha.api.worker.qwen;

public record ProcessExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut
) {
    public boolean isSuccess() {
        return !timedOut && exitCode == 0;
    }
}
