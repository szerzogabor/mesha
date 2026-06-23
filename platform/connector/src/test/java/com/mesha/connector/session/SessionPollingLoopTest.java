package com.mesha.connector.session;

import com.mesha.connector.agent.AgentRegistrationException;
import com.mesha.connector.agent.AgentRegistrationService;
import com.mesha.connector.config.SessionPollingProperties;
import com.mesha.connector.config.SessionPollingProperties.BackoffProperties;
import com.mesha.connector.session.dto.ClaimedSessionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionPollingLoopTest {

    @Mock private ConnectorAgentSessionClient client;
    @Mock private SessionProcessor sessionProcessor;
    @Mock private AgentRegistrationService agentRegistrationService;

    private UUID agentId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentId = UUID.randomUUID();
    }

    @Test
    void run_claimsAndProcessesSession_thenStopsWhenInterrupted() {
        SessionPollingProperties properties = new SessionPollingProperties(5, 60_000, new BackoffProperties(10, 100, 2.0));
        SessionPollingLoop loop = new SessionPollingLoop(client, sessionProcessor, properties, agentRegistrationService);
        ClaimedSessionResponse claimed = mock(ClaimedSessionResponse.class);
        AtomicInteger calls = new AtomicInteger();
        when(client.claimNext(agentId)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                return Optional.of(claimed);
            }
            Thread.currentThread().interrupt();
            return Optional.empty();
        });

        loop.run(agentId);

        verify(sessionProcessor).process(claimed);
    }

    @Test
    void run_backsOffExponentiallyOnRepeatedFailure() {
        SessionPollingProperties properties = new SessionPollingProperties(5, 60_000, new BackoffProperties(5, 20, 2.0));
        SessionPollingLoop loop = new SessionPollingLoop(client, sessionProcessor, properties, agentRegistrationService);
        AtomicInteger calls = new AtomicInteger();
        when(client.claimNext(agentId)).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call <= 3) {
                throw new SessionPollingException("backend down");
            }
            Thread.currentThread().interrupt();
            return Optional.empty();
        });

        loop.run(agentId);

        verify(client, times(4)).claimNext(agentId);
        verify(sessionProcessor, never()).process(any());
    }

    @Test
    void run_doesNothingWhenAlreadyInterrupted() {
        SessionPollingProperties properties = new SessionPollingProperties(5, 60_000, new BackoffProperties(5, 20, 2.0));
        SessionPollingLoop loop = new SessionPollingLoop(client, sessionProcessor, properties, agentRegistrationService);

        Thread.currentThread().interrupt();
        loop.run(agentId);
        Thread.interrupted();

        verifyNoInteractions(client);
        verifyNoInteractions(agentRegistrationService);
    }

    @Test
    void run_sendsHeartbeatOnStartAndWhenIntervalElapses() {
        SessionPollingProperties properties = new SessionPollingProperties(5, 0, new BackoffProperties(5, 20, 2.0));
        SessionPollingLoop loop = new SessionPollingLoop(client, sessionProcessor, properties, agentRegistrationService);
        AtomicInteger calls = new AtomicInteger();
        when(client.claimNext(agentId)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() >= 3) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        });

        loop.run(agentId);

        verify(agentRegistrationService, atLeast(3)).heartbeat();
    }

    @Test
    void run_continuesPollingWhenHeartbeatFails() {
        SessionPollingProperties properties = new SessionPollingProperties(5, 0, new BackoffProperties(5, 20, 2.0));
        SessionPollingLoop loop = new SessionPollingLoop(client, sessionProcessor, properties, agentRegistrationService);
        when(agentRegistrationService.heartbeat()).thenThrow(new AgentRegistrationException("not registered"));
        AtomicInteger calls = new AtomicInteger();
        when(client.claimNext(agentId)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() >= 2) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        });

        loop.run(agentId);

        verify(client, atLeast(2)).claimNext(agentId);
    }
}
