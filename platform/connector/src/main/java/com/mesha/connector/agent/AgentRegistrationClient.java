package com.mesha.connector.agent;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/** Talks to the backend API's /api/connector/agents/* endpoints. */
@Component
class AgentRegistrationClient {

    private final RestClient backendApiRestClient;

    AgentRegistrationClient(RestClient backendApiRestClient) {
        this.backendApiRestClient = backendApiRestClient;
    }

    AgentResponse register(String hostname, String executorType, String connectorVersion, List<String> capabilities) {
        try {
            return backendApiRestClient.post()
                    .uri("/api/connector/agents/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RegisterAgentRequest(hostname, executorType, connectorVersion, capabilities))
                    .retrieve()
                    .body(AgentResponse.class);
        } catch (HttpClientErrorException e) {
            throw new AgentRegistrationException("Registration failed: backend rejected the request (" + e.getStatusCode() + ")", e);
        } catch (ResourceAccessException e) {
            throw new AgentRegistrationException("Registration failed: could not reach the backend (" + e.getMessage() + ")", e);
        }
    }

    AgentResponse heartbeat(UUID agentId) {
        try {
            return backendApiRestClient.post()
                    .uri("/api/connector/agents/{agentId}/heartbeat", agentId)
                    .retrieve()
                    .body(AgentResponse.class);
        } catch (HttpClientErrorException e) {
            throw new AgentRegistrationException("Heartbeat failed: backend rejected the request (" + e.getStatusCode() + ")", e);
        } catch (ResourceAccessException e) {
            throw new AgentRegistrationException("Heartbeat failed: could not reach the backend (" + e.getMessage() + ")", e);
        }
    }
}
