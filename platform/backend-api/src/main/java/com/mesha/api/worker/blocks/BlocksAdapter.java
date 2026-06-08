package com.mesha.api.worker.blocks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesha.api.worker.observability.WorkflowTracer;
import com.mesha.api.worker.orchestration.ProviderAdapter;
import com.mesha.api.worker.orchestration.SessionRequest;
import com.mesha.api.worker.orchestration.SessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

    @Autowired
    public BlocksAdapter(WorkflowTracer workflowTracer,
                         @Qualifier("blocksRestClient") RestClient restClient,
                         @Value("${mesha.blocks.agent-name:claude}") String agentName) {
        this.workflowTracer = workflowTracer;
        this.restClient = restClient;
        this.agentName = agentName;
    }

    BlocksAdapter(WorkflowTracer workflowTracer, RestClient restClient) {
        this(workflowTracer, restClient, "claude");
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

        String message = buildMessage(request);
        int commentCount = request.comments() != null ? request.comments().size() : 0;

        log.info("session_create_start provider={} issue_id={} session_id={} comment_count={} prompt_size={} repo={}",
                providerName(), request.issueId(), localSessionId,
                commentCount, message.length(),
                request.repositoryUrl() != null ? request.repositoryUrl() : "none");

        try {
            var body = new CreateSessionRequest(agentName, message);

            var response = restClient.post()
                    .uri("/rest/v1/sessions")
                    .headers(h -> h.set("Authorization", "ApiKey " + request.apiKey()))
                    .body(body)
                    .retrieve()
                    .body(CreateSessionResponse.class);

            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new IllegalStateException("Blocks API returned empty or missing session_id");
            }

            log.info("session_create_success provider={} issue_id={} provider_session_id={}",
                    providerName(), request.issueId(), response.id());

            return new SessionResult(response.id(), SessionResult.SessionStatus.PENDING, null);

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
    public SessionResult pollSession(String sessionId) {
        MDC.put("provider", providerName());
        MDC.put("sessionId", sessionId);

        log.debug("session_poll_start provider={} provider_session_id={}", providerName(), sessionId);

        try {
            var response = restClient.get()
                    .uri("/rest/v1/sessions/{id}", sessionId)
                    .retrieve()
                    .body(PollSessionResponse.class);

            if (response == null) {
                throw new IllegalStateException("Blocks API returned empty response for session poll");
            }

            SessionResult.SessionStatus status = mapStatus(response.status());
            log.debug("session_poll_result provider={} provider_session_id={} status={}",
                    providerName(), sessionId, status);

            return new SessionResult(sessionId, status, response.finalMessage());

        } catch (RestClientException e) {
            workflowTracer.capturePollingFailure(providerName(), sessionId, 1, e);
            throw e;
        } catch (Exception e) {
            workflowTracer.capturePollingFailure(providerName(), sessionId, 1, e);
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

    private String buildMessage(SessionRequest request) {
        var sb = new StringBuilder();

        sb.append("You are working inside Mesha. Do not search Linear. Do not require Linear issue IDs. ")
          .append("Use only the information contained in this session.\n\n");

        sb.append("## Issue\n\n");
        appendField(sb, "ID", request.issueId());
        appendField(sb, "Title", request.issueTitle());
        appendField(sb, "Status", request.issueStatus());
        appendField(sb, "Priority", request.issuePriority());
        appendField(sb, "Assignee", request.issueAssigneeName());
        appendField(sb, "Created", request.issueCreatedAt());
        appendField(sb, "Updated", request.issueUpdatedAt());

        if (request.issueLabels() != null && !request.issueLabels().isEmpty()) {
            sb.append("**Labels:** ").append(String.join(", ", request.issueLabels())).append("\n");
        }

        if (request.issueDescription() != null && !request.issueDescription().isBlank()) {
            sb.append("\n### Description\n\n").append(request.issueDescription()).append("\n");
        }

        sb.append("\n## Project Context\n\n");
        appendField(sb, "Workspace", request.workspaceName());
        appendField(sb, "Project", request.projectName());

        boolean hasRepo = request.repositoryName() != null || request.repositoryUrl() != null;
        if (hasRepo) {
            sb.append("\n## Repository\n\n");
            appendField(sb, "Name", request.repositoryName());
            appendField(sb, "URL", request.repositoryUrl());
            appendField(sb, "Default Branch", request.repositoryDefaultBranch());
        }

        List<String> comments = request.comments();
        if (comments != null && !comments.isEmpty()) {
            sb.append("\n## Comments\n\n");
            for (String comment : comments) {
                sb.append(comment).append("\n\n");
            }
        }

        sb.append("\n## Task\n\n");
        sb.append("Implement the issue described above. Commit your changes and open a pull request.");

        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("**").append(label).append(":** ").append(value).append("\n");
        }
    }

    // --- Request / Response DTOs ---

    record CreateSessionRequest(
            @JsonProperty("agent_name") String agentName,
            @JsonProperty("message") String message
    ) {}

    record CreateSessionResponse(
            @JsonProperty("id") String id,
            @JsonProperty("status") String status
    ) {}

    record PollSessionResponse(
            @JsonProperty("id") String id,
            @JsonProperty("status") String status,
            @JsonProperty("final_message") String finalMessage
    ) {}
}
