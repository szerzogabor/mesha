package com.mesha.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector")
public record ConnectorProperties(
        String name,
        String environment
) {}
