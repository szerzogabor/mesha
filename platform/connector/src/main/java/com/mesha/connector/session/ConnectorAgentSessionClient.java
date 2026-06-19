package com.mesha.connector.session;

import com.mesha.connector.session.dto.ClaimSessionRequest;
import com.mesha.connector.session.dto.ClaimedSessionResponse;
import com.mesha.connector.session.dto.SessionContextResponse;
import com.mesha.connector.session.dto.UpdateSessionStatusRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/** Talks to the backend API's {@code /api/connector/agent-sessions/*} endpoints. */
@Component
public class ConnectorAgentSessionClient {

    private final RestClient backendApiRestClient;

    public ConnectorAgentSessionClient(RestClient backendApiRestClient) {
        this.backendApiRestClient = backendApiRestClient;
    }

    /** Atomically claims the next queued session for {@code agentId}, or empty if none is queued. */
    public Optional<ClaimedSessionResponse> claimNext(UUID agentId) {
        try {
            ClaimedSessionResponse response = backendApiRestClient.post()
                    .uri("/api/connector/agent-sessions/claim")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ClaimSessionRequest(agentId))
                    .retrieve()
                    .body(ClaimedSessionResponse.class);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException e) {
            throw new SessionPollingException("Claim failed: backend rejected the request (" + e.getStatusCode() + ")", e);
        } catch (ResourceAccessException e) {
            throw new SessionPollingException("Claim failed: could not reach the backend (" + e.getMessage() + ")", e);
        }
    }

    public SessionContextResponse fetchContext(UUID sessionId) {
        try {
            return backendApiRestClient.get()
                    .uri("/api/connector/agent-sessions/{sessionId}/context", sessionId)
                    .retrieve()
                    .body(SessionContextResponse.class);
        } catch (HttpClientErrorException e) {
            throw new SessionPollingException("Fetching session context failed: backend rejected the request (" + e.getStatusCode() + ")", e);
        } catch (ResourceAccessException e) {
            throw new SessionPollingException("Fetching session context failed: could not reach the backend (" + e.getMessage() + ")", e);
        }
    }

    public ClaimedSessionResponse updateStatus(UUID sessionId, ConnectorSessionStatus status, String errorMessage,
                                                String branchName, String workspacePath) {
        try {
            return backendApiRestClient.post()
                    .uri("/api/connector/agent-sessions/{sessionId}/status", sessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UpdateSessionStatusRequest(status, errorMessage, branchName, workspacePath))
                    .retrieve()
                    .body(ClaimedSessionResponse.class);
        } catch (HttpClientErrorException e) {
            throw new SessionPollingException("Status update failed: backend rejected the request (" + e.getStatusCode() + ")", e);
        } catch (ResourceAccessException e) {
            throw new SessionPollingException("Status update failed: could not reach the backend (" + e.getMessage() + ")", e);
        }
    }
}
