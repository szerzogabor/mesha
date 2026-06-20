package com.mesha.api.worker.qwen;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record ProcessExecutionRequest(
        String executionId,
        List<String> command,
        Path workingDirectory,
        Map<String, String> environment,
        String stdin,
        Duration timeout
) {}
