package com.mesha.worker.blocks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesha.worker.observability.WorkflowTracer;
import com.mesha.worker.orchestration.ProviderAdapter;
import com.mesha.worker.orchestration.SessionRequest;
import com.mesha.worker.orchestration.SessionResult;
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
        log.info("session_create_start provider={} issue_id={} session_id={} repo={} comment_count={} prompt_size={}",
                providerName(), request.issueId(), localSessionId,
                request.repositoryUrl() != null ? request.repositoryUrl() : "none",
                request.comments() != null ? request.comments().size() : 0,
                message.length());

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

        log.info("session_poll_start provider={} session_id={}", providerName(), sessionId);

        try {
            var response = restClient.get()
                    .uri("/rest/v1/sessions/{id}", sessionId)
                    .retrieve()
                    .body(PollSessionResponse.class);

            if (response == null) {
                throw new IllegalStateException("Blocks API returned empty response");
            }

            SessionResult.SessionStatus status = mapStatus(response.status());
            log.info("session_poll_result provider={} session_id={} status={}",
                    providerName(), sessionId, status);

            return new SessionResult(sessionId, status, response.finalMessage());

        } catch (RestClientException e) {
            workflowTracer.capturePollingFailure(providerName(), sessionId, 1, e);
            throw e;
        } catch (Exception e) {
            workflowTracer.capturePollingFailure(providerName(), sessionId, 1, e);
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
        var sb = new StringBuilder();

        sb.append("You are working inside Mesha.\n");
        sb.append("The complete issue context is provided below.\n");
        sb.append("Do not search Linear.\n");
        sb.append("Do not require Linear issue IDs.\n");
        sb.append("Use only the information contained in this session.\n\n");
        sb.append("---\n\n");

        sb.append("## Issue\n\n");
        if (request.issueId() != null) {
            sb.append("**ID:** ").append(request.issueId()).append("\n");
        }
        if (request.issueTitle() != null && !request.issueTitle().isBlank()) {
            sb.append("**Title:** ").append(request.issueTitle()).append("\n");
        }
        if (request.issueStatus() != null) {
            sb.append("**Status:** ").append(request.issueStatus()).append("\n");
        }
        if (request.issuePriority() != null) {
            sb.append("**Priority:** ").append(request.issuePriority()).append("\n");
        }
        if (request.issueAssigneeName() != null) {
            sb.append("**Assignee:** ").append(request.issueAssigneeName()).append("\n");
        }
        if (request.issueLabels() != null && !request.issueLabels().isEmpty()) {
            sb.append("**Labels:** ").append(String.join(", ", request.issueLabels())).append("\n");
        }
        if (request.issueCreatedAt() != null) {
            sb.append("**Created:** ").append(request.issueCreatedAt()).append("\n");
        }
        if (request.issueUpdatedAt() != null) {
            sb.append("**Updated:** ").append(request.issueUpdatedAt()).append("\n");
        }
        sb.append("\n");

        if (request.issueDescription() != null && !request.issueDescription().isBlank()) {
            sb.append("### Description\n\n");
            sb.append(request.issueDescription()).append("\n\n");
        }

        boolean hasProjectContext = request.workspaceName() != null
                || request.projectName() != null
                || request.repositoryName() != null;
        if (hasProjectContext) {
            sb.append("---\n\n## Project Context\n\n");
            if (request.workspaceName() != null) {
                sb.append("**Workspace:** ").append(request.workspaceName()).append("\n");
            }
            if (request.projectName() != null) {
                sb.append("**Project:** ").append(request.projectName()).append("\n");
            }
            if (request.repositoryName() != null) {
                sb.append("**Repository:** ").append(request.repositoryName()).append("\n");
            }
            if (request.repositoryUrl() != null) {
                sb.append("**Repository URL:** ").append(request.repositoryUrl()).append("\n");
            }
            if (request.repositoryDefaultBranch() != null) {
                sb.append("**Default Branch:** ").append(request.repositoryDefaultBranch()).append("\n");
            }
            sb.append("\n");
        }

        if (request.comments() != null && !request.comments().isEmpty()) {
            sb.append("---\n\n## Comments\n\n");
            for (String comment : request.comments()) {
                sb.append(comment).append("\n\n");
            }
        }

        sb.append("---\n\n");
        sb.append("Implement this issue. Start immediately.");

        return sb.toString();
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
