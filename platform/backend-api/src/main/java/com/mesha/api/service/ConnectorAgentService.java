package com.mesha.api.service;

import com.mesha.api.dto.RegisterConnectorAgentRequest;
import com.mesha.api.model.ConnectorAgent;
import com.mesha.api.repository.ConnectorAgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectorAgentService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorAgentService.class);

    private final ConnectorAgentRepository connectorAgentRepository;
    private final Duration offlineTimeout;

    public ConnectorAgentService(ConnectorAgentRepository connectorAgentRepository,
                                  @Value("${mesha.agents.offline-timeout-seconds:90}") long offlineTimeoutSeconds) {
        this.connectorAgentRepository = connectorAgentRepository;
        this.offlineTimeout = Duration.ofSeconds(offlineTimeoutSeconds);
    }

    public Duration getOfflineTimeout() {
        return offlineTimeout;
    }

    /**
     * Registers a connector instance, or re-registers it if one already exists for this
     * user/hostname/executor combination — so restarting the connector ("reconnect") keeps the
     * same agent identity instead of creating a duplicate row.
     */
    @Transactional
    public ConnectorAgent register(UUID userId, RegisterConnectorAgentRequest req) {
        ConnectorAgent agent = connectorAgentRepository
            .findByUserIdAndHostnameAndExecutorType(userId, req.hostname(), req.executorType())
            .orElseGet(ConnectorAgent::new);

        boolean isNew = agent.getId() == null;
        agent.setUserId(userId);
        agent.setHostname(req.hostname());
        agent.setExecutorType(req.executorType());
        agent.setConnectorVersion(req.connectorVersion());
        agent.setCapabilities(req.capabilities());
        agent.setLastSeenAt(Instant.now());
        if (isNew) {
            agent.setRegisteredAt(Instant.now());
        }
        agent = connectorAgentRepository.save(agent);

        log.info("connector_agent_registered action={} agentId={} userId={} hostname={} executorType={}",
            isNew ? "created" : "reconnected", agent.getId(), userId, req.hostname(), req.executorType());
        return agent;
    }

    @Transactional
    public ConnectorAgent heartbeat(UUID userId, UUID agentId) {
        ConnectorAgent agent = connectorAgentRepository.findByIdAndUserId(agentId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        agent.setLastSeenAt(Instant.now());
        return connectorAgentRepository.save(agent);
    }

    public List<ConnectorAgent> listForUser(UUID userId) {
        return connectorAgentRepository.findByUserIdOrderByRegisteredAtDesc(userId);
    }

    public List<ConnectorAgent> listOnlineForUser(UUID userId) {
        Instant threshold = Instant.now().minus(offlineTimeout);
        return connectorAgentRepository.findByUserIdAndLastSeenAtAfter(userId, threshold);
    }

    public ConnectorAgent getForUser(UUID agentId, UUID userId) {
        return connectorAgentRepository.findByIdAndUserId(agentId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    }
}
