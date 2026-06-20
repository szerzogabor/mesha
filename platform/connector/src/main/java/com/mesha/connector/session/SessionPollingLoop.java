package com.mesha.connector.session;

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
 */
@Component
public class SessionPollingLoop {

    private static final Logger log = LoggerFactory.getLogger(SessionPollingLoop.class);

    private final ConnectorAgentSessionClient client;
    private final SessionProcessor sessionProcessor;
    private final SessionPollingProperties properties;

    public SessionPollingLoop(ConnectorAgentSessionClient client, SessionProcessor sessionProcessor,
                               SessionPollingProperties properties) {
        this.client = client;
        this.sessionProcessor = sessionProcessor;
        this.properties = properties;
    }

    /** Polls for {@code agentId} until the current thread is interrupted. */
    public void run(UUID agentId) {
        long backoffMs = properties.backoff().baseMs();
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
        }
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
