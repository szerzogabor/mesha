package com.mesha.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connector.context")
public record TaskContextProperties(int maxDescriptionChars) {}
