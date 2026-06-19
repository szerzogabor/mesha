package com.mesha.connector.agent;

import com.mesha.connector.config.ConnectorProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/** Owns registering this connector instance as a Mesha agent and sending its heartbeats. */
@Service
public class AgentRegistrationService {

    private final AgentRegistrationClient agentRegistrationClient;
    private final AgentRegistrationStore agentRegistrationStore;
    private final ConnectorProperties properties;

    public AgentRegistrationService(AgentRegistrationClient agentRegistrationClient,
                                     AgentRegistrationStore agentRegistrationStore,
                                     ConnectorProperties properties) {
        this.agentRegistrationClient = agentRegistrationClient;
        this.agentRegistrationStore = agentRegistrationStore;
        this.properties = properties;
    }

    public AgentResponse register(String hostname, String executorType, List<String> capabilities) {
        AgentResponse response = agentRegistrationClient.register(hostname, executorType, properties.version(), capabilities);
        agentRegistrationStore.save(new AgentRegistration(response.id(), response.hostname(), response.executorType()));
        return response;
    }

    public AgentResponse heartbeat() {
        AgentRegistration registration = agentRegistrationStore.load()
                .orElseThrow(() -> new AgentRegistrationException("Not registered. Run the `register` command first."));
        return agentRegistrationClient.heartbeat(registration.agentId());
    }
}
