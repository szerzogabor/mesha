package com.mesha.api.worker.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mesha.polling")
public record PollingProperties(
        long intervalMs,
        long maxSessionAgeHours,
        BackoffProperties backoff
) {
    public record BackoffProperties(
            long baseMs,
            long maxMs,
            double multiplier
    ) {}
}
