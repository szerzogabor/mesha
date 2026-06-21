package com.mesha.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binding for {@code connector.auto-connect.*} — enables the connector to perform login and
 * agent registration automatically when started, using environment variables instead of manual
 * {@code login} / {@code register} commands.
 *
 * <p>Set {@code CONNECTOR_AUTO_CONNECT_ENABLED=true} together with
 * {@code CONNECTOR_AUTO_CONNECT_TOKEN} and {@code CONNECTOR_EXECUTOR_TYPE} to activate.
 */
@ConfigurationProperties(prefix = "connector.auto-connect")
public record AutoConnectProperties(
        boolean enabled,
        String meshaToken,
        String executorType,
        List<String> capabilities,
        String hostname
) {}
