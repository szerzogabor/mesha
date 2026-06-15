package com.mesha.api.worker.blocks;

import com.fasterxml.jackson.annotation.JsonAlias;
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
import java.util.stream.Collectors;

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
            String effectiveAgentName = (request.agentLlm() != null && !request.agentLlm().isBlank())
                    ? request.agentLlm()
                    : agentName;
            var body = new CreateSessionRequest(effectiveAgentName, message);

            var response = restClient.post()
                    .uri("/rest/v1/sessions")
                    .headers(h -> h.set("Authorization", "ApiKey " + request.apiKey()))
                    .body(body)
                    .retrieve()
                    .body(CreateSessionResponse.class);

            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new IllegalStateException("Blocks API returned empty or missing session_id");
            }

            log.info("session_create_success provider={} issue_id={} provider_session_id={} workspace_id={}",
                    providerName(), request.issueId(), response.id(),
                    response.workspaceId() != null ? response.workspaceId() : "not returned");

            return new SessionResult(response.id(), SessionResult.SessionStatus.PENDING, null, response.workspaceId(), null, null);

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
    public void cancelSession(String providerSessionId) {
        MDC.put("provider", providerName());
        MDC.put("sessionId", providerSessionId);

        log.info("session_cancel_start provider={} provider_session_id={}", providerName(), providerSessionId);

        try {
            restClient.delete()
                    .uri("/rest/v1/sessions/{id}", providerSessionId)
                    .retrieve()
                    .toBodilessEntity();

            log.info("session_cancel_success provider={} provider_session_id={}", providerName(), providerSessionId);
        } catch (RestClientException e) {
            workflowTracer.captureAiProviderFailure(providerName(), "cancelSession", 0, e);
            throw e;
        } catch (Exception e) {
            workflowTracer.captureAiProviderFailure(providerName(), "cancelSession", 0, e);
            throw new RuntimeException("Failed to cancel Blocks session: " + e.getMessage(), e);
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
            log.debug("session_poll_result provider={} provider_session_id={} status={} session_html_url={}",
                    providerName(), sessionId, status,
                    response.sessionHtmlUrl() != null ? response.sessionHtmlUrl() : "none");

            return new SessionResult(sessionId, status, response.finalMessage(), response.workspaceId(), response.sessionHtmlUrl(), null);

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

    /**
     * Creates a lightweight Blocks session for AI draft generation, using only a message and API key.
     * Returns the provider session ID on success.
     */
    public String createDraftSession(String message, String apiKey) {
        log.info("draft_session_create_start provider={} message_length={}", providerName(), message.length());
        try {
            var body = new CreateSessionRequest(agentName, message);
            var response = restClient.post()
                    .uri("/rest/v1/sessions")
                    .headers(h -> h.set("Authorization", "ApiKey " + apiKey))
                    .body(body)
                    .retrieve()
                    .body(CreateSessionResponse.class);

            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new IllegalStateException("Blocks API returned empty or missing session_id for draft session");
            }

            log.info("draft_session_create_success provider={} provider_session_id={}", providerName(), response.id());
            return response.id();
        } catch (RestClientException e) {
            workflowTracer.captureAiProviderFailure(providerName(), "createDraftSession", 0, e);
            throw e;
        } catch (Exception e) {
            workflowTracer.captureAiProviderFailure(providerName(), "createDraftSession", 0, e);
            throw new RuntimeException("Failed to create Blocks draft session: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a user message to an existing Blocks session via POST /rest/v1/sessions/{id}/messages.
     * Throws on HTTP or network errors so callers can decide whether to propagate or swallow.
     */
    public void sendUserMessage(String sessionId, String content) {
        MDC.put("provider", providerName());
        MDC.put("sessionId", sessionId);
        log.info("session_send_message_start provider={} provider_session_id={}", providerName(), sessionId);
        try {
            var body = new UserMessageRequest(content);
            restClient.post()
                    .uri("/rest/v1/sessions/{id}/messages", sessionId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("session_send_message_success provider={} provider_session_id={}", providerName(), sessionId);
        } catch (RestClientException e) {
            workflowTracer.captureAiProviderFailure(providerName(), "sendUserMessage", 0, e);
            throw e;
        } catch (Exception e) {
            workflowTracer.captureAiProviderFailure(providerName(), "sendUserMessage", 0, e);
            throw new RuntimeException("Failed to send message to Blocks session: " + e.getMessage(), e);
        } finally {
            MDC.remove("sessionId");
            MDC.remove("provider");
        }
    }

    /**
     * Fetches assistant messages for a session from the dedicated messages endpoint.
     * Returns only assistant text messages (type=message or final_message), newest-first
     * ordering is forced to asc so the caller's count-based deduplication stays correct.
     * Returns null on any error so the caller can fall back gracefully.
     */
    public List<String> fetchAssistantMessages(String sessionId) {
        MDC.put("provider", providerName());
        MDC.put("sessionId", sessionId);
        try {
            var response = restClient.get()
                    .uri("/rest/v1/sessions/{id}/messages?direction=asc&limit=100", sessionId)
                    .retrieve()
                    .body(GetMessagesResponse.class);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                return null;
            }

            List<String> messages = response.items().stream()
                    .filter(m -> "assistant".equals(m.role()))
                    .filter(m -> "message".equals(m.type()) || "final_message".equals(m.type()))
                    .filter(m -> m.message() != null && !m.message().isBlank())
                    .map(SessionMessage::message)
                    .collect(Collectors.toList());

            log.debug("session_messages_fetched provider={} provider_session_id={} count={}",
                    providerName(), sessionId, messages.size());

            return messages.isEmpty() ? null : messages;

        } catch (Exception e) {
            log.warn("session_messages_fetch_failed provider={} provider_session_id={} error={}",
                    providerName(), sessionId, e.getMessage());
            return null;
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
        var body = new StringBuilder();

        if (request.agentSystemPrompt() != null && !request.agentSystemPrompt().isBlank()) {
            body.append(request.agentSystemPrompt().trim()).append("\n\n");
        }

        body.append("You have been delegated Mesh Issue\n\n");

        // build the rest of the body via the shared builder reference
        var sb = body;

        sb.append("Issue\n\n");
        appendField(sb, "ID", request.issueId());
        appendField(sb, "Identifier", request.issueIdentifier());
        appendField(sb, "Title", request.issueTitle());
        appendField(sb, "Status", request.issueStatus());
        appendField(sb, "Priority", request.issuePriority());
        appendField(sb, "Assignee", request.issueAssigneeName());
        appendField(sb, "Created", request.issueCreatedAt());
        appendField(sb, "Updated", request.issueUpdatedAt());

        if (request.issueLabels() != null && !request.issueLabels().isEmpty()) {
            sb.append("Labels: ").append(String.join(", ", request.issueLabels())).append("\n");
        }

        if (request.issueDescription() != null && !request.issueDescription().isBlank()) {
            sb.append("\nDescription\n\n").append(request.issueDescription()).append("\n");
        }

        boolean hasProjectContext = request.workspaceName() != null || request.projectName() != null;
        if (hasProjectContext) {
            sb.append("\nProject Context\n\n");
            appendField(sb, "Workspace", request.workspaceName());
            appendField(sb, "Project", request.projectName());
        }

        boolean hasRepo = request.repositoryName() != null || request.repositoryUrl() != null;
        if (hasRepo) {
            sb.append("\nRepository\n\n");
            appendField(sb, "Name", request.repositoryName());
            appendField(sb, "URL", request.repositoryUrl());
            appendField(sb, "Default Branch", request.repositoryDefaultBranch());
        }

        List<String> comments = request.comments();
        if (comments != null && !comments.isEmpty()) {
            sb.append("\nComments\n\n");
            for (String comment : comments) {
                sb.append(comment).append("\n\n");
            }
        }

        if (request.instructions() != null && !request.instructions().isBlank()) {
            sb.append("\nAdditional Instructions\n\n").append(request.instructions()).append("\n");
        }

        if (request.issueIdentifier() != null && !request.issueIdentifier().isBlank()) {
            sb.append("\nPR Title Convention\n\n");
            sb.append("When opening a pull request for this issue, prefix the title with the issue identifier. ");
            sb.append("Format: '").append(request.issueIdentifier()).append(": <description>'\n");
            sb.append("Example: '").append(request.issueIdentifier()).append(": Add feature X'\n");
            sb.append("If no ticket ID is available, use a descriptive title without a prefix.\n");
        }

        String bodyContent = sb.toString();

        // Startup commands must be the very first text, each on its own line
        List<String> cmds = request.agentStartupCommands();
        if (cmds != null && !cmds.isEmpty()) {
            var prefix = new StringBuilder();
            for (String cmd : cmds) {
                if (cmd != null && !cmd.isBlank()) {
                    prefix.append(cmd.trim()).append("\n");
                }
            }
            if (!prefix.isEmpty()) {
                return prefix.toString() + "\n" + bodyContent;
            }
        }

        return bodyContent;
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    // --- Request / Response DTOs ---

    record CreateSessionRequest(
            @JsonProperty("agent_name") String agentName,
            @JsonProperty("message") String message
    ) {}

    record CreateSessionResponse(
            @JsonProperty("id") String id,
            @JsonProperty("status") String status,
            @JsonProperty("workspace_id") String workspaceId
    ) {}

    record PollSessionResponse(
            @JsonProperty("id") String id,
            @JsonProperty("status") String status,
            @JsonProperty("final_message") String finalMessage,
            @JsonProperty("workspace_id") String workspaceId,
            @JsonProperty("session_html_url") String sessionHtmlUrl
    ) {}

    record GetMessagesResponse(
            @JsonProperty("items") List<SessionMessage> items
    ) {}

    record SessionMessage(
            @JsonProperty("id") String id,
            @JsonProperty("role") String role,
            @JsonProperty("type") String type,
            @JsonProperty("message") @JsonAlias({"content", "text", "body"}) String message,
            @JsonProperty("created_at") String createdAt
    ) {}

    record UserMessageRequest(
            @JsonProperty("message") String message
    ) {}
}
