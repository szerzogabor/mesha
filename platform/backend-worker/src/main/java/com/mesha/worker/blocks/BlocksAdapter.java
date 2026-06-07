package com.mesha.worker.blocks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesha.worker.observability.WorkflowTracer;
import com.mesha.worker.orchestration.ProviderAdapter;
import com.mesha.worker.orchestration.SessionRequest;
import com.mesha.worker.orchestration.SessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Blocks AI platform adapter. HTTP calls are auto-instrumented by the
 * OpenTelemetry Java Agent; WorkflowTracer handles structured logging and error capture.
 */
@Component
public class BlocksAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(BlocksAdapter.class);

    private final WorkflowTracer workflowTracer;
    private final RestClient restClient;

    public BlocksAdapter(WorkflowTracer workflowTracer,
                         @Qualifier("blocksRestClient") RestClient restClient) {
        this.workflowTracer = workflowTracer;
        this.restClient = restClient;
    }

    @Override
    public String providerName() {
        return "blocks";
    }

    @Override
    public SessionResult createSession(SessionRequest request) {
        String localSessionId = UUID.randomUUID().toString();
        MDC.put("sessionId", localSessionId);
        MDC.put("provider", providerName());

        log.info("session_create_start provider={} issue_id={} session_id={}",
                providerName(), request.issueId(), localSessionId);

        try {
            var body = new CreateSessionRequest(
                    request.issueId(),
                    request.issueTitle(),
                    request.issueDescription(),
                    request.repositoryContext()
            );

            var response = restClient.post()
                    .uri("/sessions")
                    .headers(h -> h.set("Authorization", "Bearer " + request.apiKey()))
                    .body(body)
                    .retrieve()
                    .body(CreateSessionResponse.class);

            if (response == null || response.sessionId() == null) {
                throw new IllegalStateException("Blocks API returned empty or missing session_id");
            }

            log.info("session_create_success provider={} issue_id={} provider_session_id={}",
                    providerName(), request.issueId(), response.sessionId());

            return new SessionResult(response.sessionId(), SessionResult.SessionStatus.PENDING, null);

        } catch (RestClientException e) {
            workflowTracer.captureAiProviderFailure(providerName(), "createSession", 0, e);
            throw e;
        } catch (Exception e) {
            workflowTracer.captureAiProviderFailure(providerName(), "createSession", 0, e);
            throw new RuntimeException("Failed to create Blocks session: " + e.getMessage(), e);
        } finally {
            MDC.remove("sessionId");
            MDC.remove("provider");
        }
    }

    @Override
    public SessionResult pollSession(String providerSessionId) {
        MDC.put("sessionId", providerSessionId);
        MDC.put("provider", providerName());

        log.info("session_poll_start provider={} session_id={}", providerName(), providerSessionId);

        try {
            var response = restClient.get()
                    .uri("/sessions/{id}", providerSessionId)
                    .retrieve()
                    .body(PollSessionResponse.class);

            if (response == null) {
                throw new IllegalStateException("Blocks API returned empty response for session " + providerSessionId);
            }

            SessionResult.SessionStatus status = mapStatus(response.status());

            log.info("session_poll_result provider={} session_id={} status={}",
                    providerName(), providerSessionId, status);

            return new SessionResult(providerSessionId, status, response.finalMessage());

        } catch (RestClientException e) {
            workflowTracer.capturePollingFailure(providerName(), providerSessionId, 1, e);
            throw e;
        } catch (Exception e) {
            workflowTracer.capturePollingFailure(providerName(), providerSessionId, 1, e);
            throw new RuntimeException("Failed to poll Blocks session: " + e.getMessage(), e);
        } finally {
            MDC.remove("sessionId");
            MDC.remove("provider");
        }
    }

    SessionResult.SessionStatus mapStatus(String blocksStatus) {
        if (blocksStatus == null) {
            return SessionResult.SessionStatus.PENDING;
        }
        return switch (blocksStatus.toLowerCase()) {
            case "pending" -> SessionResult.SessionStatus.PENDING;
            case "running", "in_progress" -> SessionResult.SessionStatus.RUNNING;
            case "completed", "done", "succeeded" -> SessionResult.SessionStatus.COMPLETED;
            case "failed", "error", "cancelled", "canceled" -> SessionResult.SessionStatus.FAILED;
            default -> {
                log.warn("session_unknown_status provider={} raw_status={} — treating as PENDING",
                        providerName(), blocksStatus);
                yield SessionResult.SessionStatus.PENDING;
            }
        };
    }

    // --- Request / Response DTOs (package-private for testability) ---

    record CreateSessionRequest(
            @JsonProperty("issue_id") String issueId,
            @JsonProperty("issue_title") String issueTitle,
            @JsonProperty("issue_description") String issueDescription,
            @JsonProperty("repository_context") String repositoryContext
    ) {}

    record CreateSessionResponse(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("status") String status
    ) {}

    record PollSessionResponse(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("status") String status,
            @JsonProperty("final_message") String finalMessage
    ) {}
}
