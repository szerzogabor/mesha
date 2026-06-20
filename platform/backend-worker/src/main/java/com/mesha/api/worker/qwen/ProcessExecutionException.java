package com.mesha.api.worker.qwen;

public class ProcessExecutionException extends RuntimeException {

    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
