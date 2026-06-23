package com.mesha.connector.auto;

import com.mesha.connector.agent.AgentRegistrationService;
import com.mesha.connector.agent.AgentRegistrationStore;
import com.mesha.connector.auth.ConnectorAuthService;
import com.mesha.connector.config.AutoConnectProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Performs automatic login and agent registration when {@code connector.auto-connect.enabled=true},
 * so the connector can start polling without prior manual {@code login} / {@code register} commands.
 *
 * <p>Required env vars:
 * <ul>
 *   <li>{@code CONNECTOR_AUTO_CONNECT_ENABLED=true}</li>
 *   <li>{@code CONNECTOR_AUTO_CONNECT_TOKEN} — connector access token ({@code mcat_...}) from the web app</li>
 *   <li>{@code CONNECTOR_AUTO_CONNECT_EXECUTOR_TYPE} — executor type (e.g. {@code cli}, {@code docker})</li>
 * </ul>
 * Optional:
 * <ul>
 *   <li>{@code CONNECTOR_AUTO_CONNECT_CAPABILITIES} — comma-separated capability strings</li>
 *   <li>{@code CONNECTOR_AUTO_CONNECT_HOSTNAME} — hostname override; auto-detected when absent</li>
 * </ul>
 */
@Service
public class AutoConnectService {

    private static final Logger log = LoggerFactory.getLogger(AutoConnectService.class);

    private final AutoConnectProperties properties;
    private final ConnectorAuthService connectorAuthService;
    private final AgentRegistrationService agentRegistrationService;
    private final AgentRegistrationStore agentRegistrationStore;

    public AutoConnectService(AutoConnectProperties properties,
                              ConnectorAuthService connectorAuthService,
                              AgentRegistrationService agentRegistrationService,
                              AgentRegistrationStore agentRegistrationStore) {
        this.properties = properties;
        this.connectorAuthService = connectorAuthService;
        this.agentRegistrationService = agentRegistrationService;
        this.agentRegistrationStore = agentRegistrationStore;
    }

    /**
     * When auto-connect is enabled, ensures the connector is authenticated and has a registered
     * agent identity before the polling loop starts. No-ops when auto-connect is disabled.
     *
     * @throws AutoConnectException if auto-connect config is incomplete or backend calls fail
     */
    public void ensureConnected() {
        if (!properties.enabled()) {
            return;
        }

        validateConfig();

        if (!connectorAuthService.isAuthenticated()) {
            log.info("Auto-connect: performing login...");
            connectorAuthService.login(properties.meshaToken());
            log.info("Auto-connect: login successful");
        } else {
            log.info("Auto-connect: already authenticated, skipping login");
        }

        if (agentRegistrationStore.load().isEmpty()) {
            String hostname = resolveHostname();
            List<String> capabilities = properties.capabilities() == null ? List.of()
                    : properties.capabilities().stream().filter(c -> !c.isBlank()).toList();
            log.info("Auto-connect: registering agent (hostname={}, executorType={})...", hostname, properties.executorType());
            agentRegistrationService.register(hostname, properties.executorType(), capabilities);
            log.info("Auto-connect: agent registration successful");
        } else {
            log.info("Auto-connect: agent already registered, skipping registration");
        }
    }

    private void validateConfig() {
        if (isBlank(properties.meshaToken())) {
            throw new AutoConnectException(
                    "Auto-connect is enabled but CONNECTOR_AUTO_CONNECT_TOKEN is not set");
        }
        if (isBlank(properties.executorType())) {
            throw new AutoConnectException(
                    "Auto-connect is enabled but CONNECTOR_AUTO_CONNECT_EXECUTOR_TYPE is not set");
        }
    }

    private String resolveHostname() {
        if (!isBlank(properties.hostname())) {
            return properties.hostname();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String envHostname = System.getenv("HOSTNAME");
            if (envHostname == null || envHostname.isBlank()) {
                envHostname = System.getenv("COMPUTERNAME");
            }
            return (envHostname != null && !envHostname.isBlank()) ? envHostname : "unknown-host";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
