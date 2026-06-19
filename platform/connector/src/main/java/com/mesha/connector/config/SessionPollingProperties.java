package com.mesha.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector.polling")
public record SessionPollingProperties(
        long intervalMs,
        BackoffProperties backoff
) {
    public record BackoffProperties(long baseMs, long maxMs, double multiplier) {}
}
