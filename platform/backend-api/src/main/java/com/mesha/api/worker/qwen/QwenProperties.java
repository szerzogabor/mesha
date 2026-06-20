package com.mesha.api.worker.qwen;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mesha.qwen")
public record QwenProperties(
        String cliPath,
        List<String> extraArgs,
        String workingDirectory,
        long timeoutSeconds
) {
    public QwenProperties {
        if (cliPath == null || cliPath.isBlank()) {
            cliPath = "qwen";
        }
        if (extraArgs == null) {
            extraArgs = List.of();
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 1800;
        }
    }
}
