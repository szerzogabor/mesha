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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
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
    private final String agentName;

    public BlocksAdapter(WorkflowTracer workflowTracer,
                         @Qualifier("blocksRestClient") RestClient restClient,
                         @Value("${mesha.blocks.agent-name:claude}") String agentName) {
        this.workflowTracer = workflowTracer;
        this.restClient = restClient;
        this.agentName = agentName;
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
            var body = new CreateSessionRequest(agentName, buildMessage(request));

            var response = restClient.post()
                    .uri("/rest/v1/sessions")
                    .headers(h -> h.set("Authorization", "ApiKey " + request.apiKey()))
                    .body(body)
                    .retrieve()
                    .body(CreateSessionResponse.class);

            if (response == null || response.id() == null
                    || response.links() == null
                    || response.links().finalMessage() == null
                    || response.links().finalMessage().href() == null) {
                throw new IllegalStateException("Blocks API returned incomplete session response");
            }

            String pollUrl = response.links().finalMessage().href();
            log.info("session_create_success provider={} issue_id={} session_id={} poll_url={}",
                    providerName(), request.issueId(), response.id(), pollUrl);

            return new SessionResult(pollUrl, SessionResult.SessionStatus.PENDING, null);

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
    public SessionResult pollSession(String pollUrl) {
        MDC.put("provider", providerName());

        log.info("session_poll_start provider={} poll_url={}", providerName(), pollUrl);

        try {
            var response = restClient.get()
                    .uri(URI.create(pollUrl))
                    .retrieve()
                    .body(FinalMessagePage.class);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                log.debug("session_poll_pending provider={}", providerName());
                return new SessionResult(pollUrl, SessionResult.SessionStatus.PENDING, null);
            }

            String finalMessage = response.items().get(0).message();
            log.info("session_poll_completed provider={}", providerName());
            return new SessionResult(pollUrl, SessionResult.SessionStatus.COMPLETED, finalMessage);

        } catch (RestClientException e) {
            workflowTracer.capturePollingFailure(providerName(), pollUrl, 1, e);
            throw e;
        } catch (Exception e) {
            workflowTracer.capturePollingFailure(providerName(), pollUrl, 1, e);
            throw new RuntimeException("Failed to poll Blocks session: " + e.getMessage(), e);
        } finally {
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

    private String buildMessage(SessionRequest request) {
        var sb = new StringBuilder("Implement the following issue:\n\n");
        if (request.issueTitle() != null && !request.issueTitle().isBlank()) {
            sb.append("**").append(request.issueTitle()).append("**\n\n");
        }
        if (request.issueDescription() != null && !request.issueDescription().isBlank()) {
            sb.append(request.issueDescription());
        }
        return sb.toString();
    }

    // --- Request / Response DTOs ---

    record CreateSessionRequest(
            @JsonProperty("agent_name") String agentName,
            @JsonProperty("message") String message
    ) {}

    record CreateSessionResponse(
            @JsonProperty("id") String id,
            @JsonProperty("_links") Links links
    ) {
        record Links(
                @JsonProperty("final_message") FinalMessageLink finalMessage
        ) {
            record FinalMessageLink(
                    @JsonProperty("href") String href
            ) {}
        }
    }

    record FinalMessagePage(
            @JsonProperty("items") List<FinalMessageItem> items
    ) {
        record FinalMessageItem(
                @JsonProperty("message") String message
        ) {}
    }
}
