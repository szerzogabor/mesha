package com.mesha.connector.session;

import com.mesha.connector.agent.AgentRegistrationException;
import com.mesha.connector.agent.AgentRegistrationService;
import com.mesha.connector.config.SessionPollingProperties;
import com.mesha.connector.session.dto.ClaimedSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Repeatedly claims the next queued session for an agent and hands it to {@link SessionProcessor}.
 * Backs off exponentially (capped at {@code connector.polling.backoff.max-ms}) when the backend is
 * unreachable or rejecting requests, and resets to the base interval as soon as polling succeeds again.
 * Also sends periodic heartbeats so the backend keeps reporting this agent as online for as long as
 * the loop is running — claiming/processing sessions alone does not refresh the agent's liveness.
 */
@Component
public class SessionPollingLoop {

    private static final Logger log = LoggerFactory.getLogger(SessionPollingLoop.class);

    private final ConnectorAgentSessionClient client;
    private final SessionProcessor sessionProcessor;
    private final SessionPollingProperties properties;
    private final AgentRegistrationService agentRegistrationService;

    public SessionPollingLoop(ConnectorAgentSessionClient client, SessionProcessor sessionProcessor,
                               SessionPollingProperties properties, AgentRegistrationService agentRegistrationService) {
        this.client = client;
        this.sessionProcessor = sessionProcessor;
        this.properties = properties;
        this.agentRegistrationService = agentRegistrationService;
    }

    /** Polls for {@code agentId} until the current thread is interrupted. */
    public void run(UUID agentId) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        long backoffMs = properties.backoff().baseMs();
        long lastHeartbeatAt = sendHeartbeat();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Optional<ClaimedSessionResponse> claimed = client.claimNext(agentId);
                backoffMs = properties.backoff().baseMs();
                if (claimed.isPresent()) {
                    sessionProcessor.process(claimed.get());
                } else {
                    sleep(properties.intervalMs());
                }
            } catch (SessionPollingException e) {
                log.warn("session_poll_failed error={} retryInMs={}", e.getMessage(), backoffMs);
                sleep(backoffMs);
                backoffMs = nextBackoff(backoffMs);
            }
            lastHeartbeatAt = maybeSendHeartbeat(lastHeartbeatAt);
        }
    }

    private long maybeSendHeartbeat(long lastHeartbeatAt) {
        if (System.currentTimeMillis() - lastHeartbeatAt < properties.heartbeatIntervalMs()) {
            return lastHeartbeatAt;
        }
        return sendHeartbeat();
    }

    private long sendHeartbeat() {
        try {
            agentRegistrationService.heartbeat();
        } catch (AgentRegistrationException e) {
            log.warn("heartbeat_failed error={}", e.getMessage());
        }
        return System.currentTimeMillis();
    }

    private long nextBackoff(long currentMs) {
        long next = (long) (currentMs * properties.backoff().multiplier());
        return Math.min(next, properties.backoff().maxMs());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
