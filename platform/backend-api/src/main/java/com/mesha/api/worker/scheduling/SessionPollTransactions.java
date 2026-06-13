package com.mesha.api.worker.scheduling;

import com.mesha.api.model.AIExecutionState;
import com.mesha.api.model.AutomationTriggerType;
import com.mesha.api.model.BlocksMessage;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.Comment;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Issue;
import com.mesha.api.repository.BlocksMessageRepository;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.repository.CommentRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.repository.WorkspaceBlocksConfigRepository;
import com.mesha.api.service.AutomationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles all database reads and writes for the polling cycle in short, discrete transactions.
 * Keeping these separate from SessionPollService ensures DB connections are released before
 * HTTP calls to the Blocks API — preventing connection pool exhaustion under load.
 */
@Service
class SessionPollTransactions {

    private static final Logger log = LoggerFactory.getLogger(SessionPollTransactions.class);

    private final BlocksSessionRepository sessionRepo;
    private final IssueRepository issueRepo;
    private final CommentRepository commentRepo;
    private final GitHubRepositoryRepository gitHubRepoRepo;
    private final BlocksMessageRepository messageRepo;
    private final WorkspaceBlocksConfigRepository configRepo;
    private final BlocksApiKeyService apiKeyService;
    private final AutomationService automationService;

    SessionPollTransactions(BlocksSessionRepository sessionRepo,
                            IssueRepository issueRepo,
                            CommentRepository commentRepo,
                            GitHubRepositoryRepository gitHubRepoRepo,
                            BlocksMessageRepository messageRepo,
                            WorkspaceBlocksConfigRepository configRepo,
                            BlocksApiKeyService apiKeyService,
                            AutomationService automationService) {
        this.sessionRepo = sessionRepo;
        this.issueRepo = issueRepo;
        this.commentRepo = commentRepo;
        this.gitHubRepoRepo = gitHubRepoRepo;
        this.messageRepo = messageRepo;
        this.configRepo = configRepo;
        this.apiKeyService = apiKeyService;
        this.automationService = automationService;
    }

    /**
     * Loads the minimal session state needed to decide how to proceed, releasing the DB
     * connection before any external HTTP call is made.
     */
    @Transactional(readOnly = true)
    SessionSnapshot loadSnapshot(UUID sessionId) {
        BlocksSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) return null;
        // Force-initialize the lazy Issue association inside this transaction
        UUID issueId = session.getIssue() != null ? session.getIssue().getId() : null;
        return new SessionSnapshot(
                session.getId(),
                issueId,
                session.getProviderSessionId(),
                session.getRetryCount(),
                session.getExecutionState(),
                session.getCreatedAt(),
                session.getSessionUrl(),
                session.getApiMessageOffset()
        );
    }

    /**
     * Loads everything needed to build a SessionRequest for a new Blocks session dispatch.
     * The DB connection is held only for this read, then released before the HTTP call.
     */
    @Transactional(readOnly = true)
    DispatchInputs loadDispatchInputs(UUID sessionId) {
        BlocksSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) return null;

        Issue issue = session.getIssue();
        if (issue == null) {
            log.error("session_dispatch_no_issue session_id={}", sessionId);
            return null;
        }

        String apiKey = apiKeyService.resolveApiKey(issue.getId()).orElse(null);
        if (apiKey == null) {
            log.error("session_dispatch_no_api_key session_id={} issue_id={}", sessionId, issue.getId());
            return null;
        }

        String projectName = issue.getProject() != null ? issue.getProject().getName() : null;
        String projectKey = issue.getProject() != null ? issue.getProject().getKey() : null;
        Integer issueNumber = issue.getNumber();
        String issueIdentifier = (projectKey != null && issueNumber != null) ? projectKey + "-" + issueNumber : null;

        String workspaceName = (issue.getProject() != null && issue.getProject().getWorkspace() != null)
                ? issue.getProject().getWorkspace().getName() : null;
        UUID workspaceId = (issue.getProject() != null && issue.getProject().getWorkspace() != null)
                ? issue.getProject().getWorkspace().getId() : null;

        String assigneeName = null;
        if (issue.getAssignee() != null) {
            String name = issue.getAssignee().getName();
            assigneeName = (name != null && !name.isBlank()) ? name : issue.getAssignee().getEmail();
        }

        List<String> labelNames = issue.getLabels().stream()
                .map(l -> l.getName())
                .collect(Collectors.toList());

        List<String> comments = loadCommentTexts(issue.getId());

        String repoName = null;
        String repoHtmlUrl = null;
        String repoDefaultBranch = null;
        if (workspaceId != null) {
            List<GitHubRepository> repos = gitHubRepoRepo.findAllByWorkspaceIdAndConnectedTrue(workspaceId);
            if (!repos.isEmpty()) {
                GitHubRepository repo = repos.get(0);
                repoName = repo.getName();
                repoHtmlUrl = repo.getHtmlUrl();
                repoDefaultBranch = repo.getDefaultBranch();
            }
        }

        return new DispatchInputs(
                issue.getId(),
                issueIdentifier,
                issue.getTitle(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getPriority() != null ? issue.getPriority().name() : null,
                assigneeName,
                labelNames,
                issue.getCreatedAt() != null ? issue.getCreatedAt().toString() : null,
                issue.getUpdatedAt() != null ? issue.getUpdatedAt().toString() : null,
                workspaceName,
                workspaceId,
                projectName,
                repoName,
                repoHtmlUrl,
                repoDefaultBranch,
                comments,
                apiKey,
                session.getInstructions(),
                issue.getAgentLlm()
        );
    }

    private List<String> loadCommentTexts(UUID issueId) {
        try {
            return commentRepo.findAllByIssueId(issueId).stream()
                    .map(c -> {
                        String author = c.getAuthor() == null ? "Unknown"
                                : (c.getAuthor().getName() != null && !c.getAuthor().getName().isBlank()
                                ? c.getAuthor().getName() : c.getAuthor().getEmail());
                        return "**" + author + "** (" + c.getCreatedAt() + "):\n" + c.getBody();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("session_dispatch_comments_load_failed issue_id={} error={}", issueId, e.getMessage());
            return List.of();
        }
    }

    @Transactional
    void saveDispatchResult(UUID sessionId, String providerSessionId, String sessionUrl) {
        sessionRepo.findById(sessionId).ifPresent(session -> {
            session.setProviderSessionId(providerSessionId);
            session.setSessionUrl(sessionUrl);
            sessionRepo.save(session);
        });
    }

    @Transactional
    void saveDispatchRetry(UUID sessionId) {
        sessionRepo.findById(sessionId).ifPresent(session -> {
            session.setRetryCount(session.getRetryCount() + 1);
            sessionRepo.save(session);
        });
    }

    @Transactional
    void persistBlocksWorkspaceId(UUID workspaceId, String blocksWorkspaceId) {
        int updated = configRepo.setBlocksWorkspaceIdIfAbsent(workspaceId, blocksWorkspaceId);
        if (updated > 0) {
            log.info("blocks_workspace_id_discovered workspace_id={} blocks_workspace_id={}", workspaceId, blocksWorkspaceId);
        }
    }

    @Transactional
    void markFailed(UUID sessionId, String reason) {
        sessionRepo.findById(sessionId).ifPresent(session -> {
            Duration age = java.time.Duration.between(session.getCreatedAt(), Instant.now());
            log.warn("session_expired session_id={} age_hours={} reason={}", sessionId, age.toHours(), reason);
            session.setExecutionState(AIExecutionState.FAILED);
            session.setErrorMessage(reason);
            sessionRepo.save(session);
            saveStateMessage(session, AIExecutionState.FAILED, reason);
            fireAutomationInternal(session, AIExecutionState.FAILED, reason);
        });
    }

    @Transactional
    void savePollResult(UUID sessionId, AIExecutionState prevState, AIExecutionState newState,
                        int newRetryCount, String sessionUrl, List<String> apiMessages, int newApiOffset,
                        boolean clearPlaceholders, String finalMessage) {
        sessionRepo.findById(sessionId).ifPresent(session -> {
            session.setRetryCount(newRetryCount);
            if (sessionUrl != null && session.getSessionUrl() == null) {
                session.setSessionUrl(sessionUrl);
                log.info("blocks_session_url_set session_id={} session_url={}", sessionId, sessionUrl);
            }

            if (newState != prevState) {
                session.setExecutionState(newState);
                log.info("session_state_changed session_id={} from={} to={} provider_session_id={}",
                        sessionId, prevState, newState, session.getProviderSessionId());
            }

            if (clearPlaceholders && messageRepo.countBySessionId(sessionId) > 0) {
                messageRepo.deleteAllBySessionId(sessionId);
                log.info("blocks_placeholder_messages_cleared session_id={}", sessionId);
            }

            if (apiMessages != null && !apiMessages.isEmpty()) {
                int startIndex = Math.min(session.getApiMessageOffset(), apiMessages.size());
                List<String> newMessages = apiMessages.subList(startIndex, apiMessages.size());
                for (String text : newMessages) {
                    BlocksMessage msg = new BlocksMessage();
                    msg.setSession(session);
                    msg.setMessage(text);
                    messageRepo.save(msg);
                }
                if (!newMessages.isEmpty()) {
                    session.setApiMessageOffset(newApiOffset);
                    log.debug("blocks_api_messages_saved session_id={} new_count={}", sessionId, newMessages.size());
                }
            } else if (newState != prevState) {
                saveStateMessage(session, newState, finalMessage);
            }

            sessionRepo.save(session);

            if (newState != prevState) {
                // Fall back to the last assistant message when the provider's final_message is
                // absent (e.g. Blocks returns null final_message on FAILED sessions and the
                // token-limit text arrives via the messages list instead).
                String effectiveMsg = finalMessage;
                if (effectiveMsg == null && apiMessages != null && !apiMessages.isEmpty()) {
                    effectiveMsg = apiMessages.get(apiMessages.size() - 1);
                }
                fireAutomationInternal(session, newState, effectiveMsg, apiMessages);
            }
        });
    }

    private void saveStateMessage(BlocksSession session, AIExecutionState state, String providerMessage) {
        String text = switch (state) {
            case PLANNING      -> "Analyzing requirements and planning implementation";
            case EXECUTING     -> "Writing code and making changes";
            case WAITING_REVIEW -> "Implementation complete, opening pull request";
            case PR_OPENED     -> "Pull request created";
            case DONE          -> providerMessage != null ? providerMessage : "Implementation successfully completed";
            case FAILED        -> providerMessage != null ? "Session failed: " + providerMessage : "Session failed";
            case CANCELED      -> "Session canceled";
            default            -> null;
        };
        if (text == null) return;
        try {
            BlocksMessage msg = new BlocksMessage();
            msg.setSession(session);
            msg.setMessage(text);
            messageRepo.save(msg);
        } catch (Exception e) {
            log.warn("blocks_message_save_failed session_id={} state={} error={}", session.getId(), state, e.getMessage());
        }
    }

    private void fireAutomationInternal(BlocksSession session, AIExecutionState state, String providerMessage) {
        fireAutomationInternal(session, state, providerMessage, null);
    }

    private void fireAutomationInternal(BlocksSession session, AIExecutionState state,
                                        String providerMessage, List<String> allMessages) {
        AutomationTriggerType trigger = switch (state) {
            case DONE   -> AutomationTriggerType.BLOCKS_SESSION_COMPLETED;
            case FAILED -> AutomationTriggerType.BLOCKS_SESSION_FAILED;
            default     -> null;
        };
        if (trigger != null) {
            automationService.executeFor(trigger, session.getIssue());
        }
        String errorMsg = session.getErrorMessage();
        boolean tokenLimitHit = isTokenLimitMessage(providerMessage)
                || isTokenLimitMessage(errorMsg)
                || anyMessageIsTokenLimit(allMessages);
        if (tokenLimitHit) {
            log.info("session_token_limit_detected session_id={}", session.getId());
            automationService.executeFor(AutomationTriggerType.AI_TOKEN_LIMIT_HIT, session.getIssue());
        }
    }

    private static final java.util.regex.Pattern TOKEN_LIMIT_PATTERN = java.util.regex.Pattern.compile(
            // Generic token/context limit terms
            "token[_ ]limit|context[_ ]limit|context[_ ]length|max[_ ]tokens|out[_ ]of[_ ]tokens|context[_ ]window"
            // Claude.ai usage limit: "You've hit your limit · resets 4:40pm (UTC)"
            + "|hit your limit"
            // Claude.ai alternate: "You're out of messages until <time>"
            + "|out of messages"
            // Claude API context overflow: "prompt is too long"
            + "|prompt is too long"
            // Claude API / OpenAI rate limiting: "rate limit reached", "rate_limit_error"
            + "|rate.?limit"
            // Gemini: "The input token count (X) exceeds the maximum number of tokens allowed (Y)"
            + "|maximum number of tokens allowed|input token count"
            // Gemini quota/rate errors: "RESOURCE_EXHAUSTED", "Resource has been exhausted",
            // "Quota exceeded for quota metric"
            + "|resource.exhausted|quota.exceeded|resource_exhausted"
            // GitHub Copilot (ghagpt): "prompt token count of X exceeds the limit of Y"
            + "|prompt token count"
            // OpenAI/ChatGPT: "insufficient_quota", "exceeded your current quota"
            + "|insufficient.quota"
            // Various providers: "token count exceeds maximum"
            + "|token count exceeds",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    boolean isTokenLimitMessage(String message) {
        return message != null && TOKEN_LIMIT_PATTERN.matcher(message).find();
    }

    boolean anyMessageIsTokenLimit(List<String> messages) {
        return messages != null && messages.stream().anyMatch(this::isTokenLimitMessage);
    }

    /** Immutable snapshot of session state loaded before any HTTP call. */
    record SessionSnapshot(
            UUID sessionId,
            UUID issueId,
            String providerSessionId,
            int retryCount,
            AIExecutionState executionState,
            Instant createdAt,
            String sessionUrl,
            int apiMessageOffset
    ) {}

    /** All data needed to build a Blocks API session creation request. */
    record DispatchInputs(
            UUID issueId,
            String issueIdentifier,
            String title,
            String description,
            String status,
            String priority,
            String assigneeName,
            List<String> labelNames,
            String createdAt,
            String updatedAt,
            String workspaceName,
            UUID workspaceId,
            String projectName,
            String repoName,
            String repoHtmlUrl,
            String repoDefaultBranch,
            List<String> comments,
            String apiKey,
            String instructions,
            String agentLlm
    ) {}
}
