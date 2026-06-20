package com.mesha.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector.workspace")
public record WorkspaceProperties(
        String root,
        String cleanupPolicy
) {}
