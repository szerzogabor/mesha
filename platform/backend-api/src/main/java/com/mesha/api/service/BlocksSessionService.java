package com.mesha.api.service;

import com.mesha.api.dto.UpdateBlocksSessionRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.BlocksMessageRepository;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.worker.blocks.BlocksAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class BlocksSessionService {

    private static final Logger log = LoggerFactory.getLogger(BlocksSessionService.class);

    private final BlocksSessionRepository blocksSessionRepository;
    private final IssueRepository issueRepository;
    private final ActivityService activityService;
    private final BlocksConfigService blocksConfigService;
    private final BlocksMessageRepository blocksMessageRepository;
    private final BlocksAdapter blocksAdapter;

    public BlocksSessionService(BlocksSessionRepository blocksSessionRepository,
                                IssueRepository issueRepository,
                                ActivityService activityService,
                                BlocksConfigService blocksConfigService,
                                BlocksMessageRepository blocksMessageRepository,
                                BlocksAdapter blocksAdapter) {
        this.blocksSessionRepository = blocksSessionRepository;
        this.issueRepository = issueRepository;
        this.activityService = activityService;
        this.blocksConfigService = blocksConfigService;
        this.blocksMessageRepository = blocksMessageRepository;
        this.blocksAdapter = blocksAdapter;
    }

    @Transactional
    public BlocksSession assignToBlocks(UUID issueId, User actor) {
        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        UUID workspaceId = issue.getProject().getWorkspace().getId();
        if (!blocksConfigService.isConnected(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                "Blocks is not connected for this workspace. Configure it in Workspace Settings → Integrations → Blocks.");
        }

        blocksSessionRepository.findActiveByIssueId(issueId).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Issue already has an active Blocks session in state: " + existing.getExecutionState());
        });

        BlocksSession session = new BlocksSession();
        session.setIssue(issue);
        session.setExecutionState(AIExecutionState.CREATED);
        session = blocksSessionRepository.save(session);

        issue.setAiAssignmentState("CREATED");
        issueRepository.save(issue);

        activityService.record(issue, actor, ActivityEventType.AI_ASSIGNED, null, session.getId().toString());

        BlocksMessage startMsg = new BlocksMessage();
        startMsg.setSession(session);
        startMsg.setMessage("Session started");
        blocksMessageRepository.save(startMsg);

        log.info("Blocks session created issueId={} sessionId={}", issueId, session.getId());
        return session;
    }

    @Transactional
    public BlocksSession updateSession(UUID sessionId, UpdateBlocksSessionRequest req, User actor) {
        BlocksSession session = blocksSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocks session not found"));

        if (session.getExecutionState() == AIExecutionState.DONE
                || session.getExecutionState() == AIExecutionState.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot update a session in terminal state: " + session.getExecutionState());
        }

        String oldState = session.getExecutionState().name();

        if (req.executionState() != null) {
            session.setExecutionState(req.executionState());
        }
        if (req.providerSessionId() != null) {
            session.setProviderSessionId(req.providerSessionId());
        }
        if (req.prUrl() != null) {
            session.setPrUrl(req.prUrl());
        }
        if (req.prNumber() != null) {
            session.setPrNumber(req.prNumber());
        }
        if (req.branchName() != null) {
            session.setBranchName(req.branchName());
        }
        if (req.errorMessage() != null) {
            session.setErrorMessage(req.errorMessage());
        }
        if (req.sessionUrl() != null) {
            session.setSessionUrl(req.sessionUrl());
            log.info("blocks_session_url_set session_id={} session_url={}", sessionId, req.sessionUrl());
        }

        session = blocksSessionRepository.save(session);

        Issue issue = session.getIssue();
        issue.setAiAssignmentState(session.getExecutionState().name());
        issueRepository.save(issue);

        ActivityEventType eventType = resolveActivityEventType(session.getExecutionState());
        activityService.record(issue, actor, eventType, oldState, session.getExecutionState().name());

        log.info("Blocks session state updated sessionId={} from={} to={}", sessionId, oldState, session.getExecutionState());
        return session;
    }

    @Transactional
    public BlocksSession cancelSession(UUID sessionId, User actor) {
        BlocksSession session = blocksSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocks session not found"));

        if (session.getExecutionState() == AIExecutionState.DONE
                || session.getExecutionState() == AIExecutionState.FAILED
                || session.getExecutionState() == AIExecutionState.CANCELED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot cancel a session already in terminal state: " + session.getExecutionState());
        }

        String oldState = session.getExecutionState().name();
        String providerSessionId = session.getProviderSessionId();
        session.setExecutionState(AIExecutionState.CANCELED);
        session = blocksSessionRepository.save(session);

        Issue issue = session.getIssue();
        issue.setAiAssignmentState("CANCELED");
        issueRepository.save(issue);

        activityService.record(issue, actor, ActivityEventType.AI_CANCELED, oldState, "CANCELED");
        log.info("blocks_session_canceled session_id={} provider_session_id={}", sessionId,
                providerSessionId != null ? providerSessionId : "none");

        if (providerSessionId != null) {
            try {
                blocksAdapter.cancelSession(providerSessionId);
                log.info("blocks_remote_cancel_success session_id={} provider_session_id={}",
                        sessionId, providerSessionId);
            } catch (Exception e) {
                log.warn("blocks_remote_cancel_failed session_id={} provider_session_id={} — local cancel stands",
                        sessionId, providerSessionId, e);
            }
        } else {
            log.info("blocks_remote_cancel_skipped session_id={} reason=no_provider_session_id", sessionId);
        }

        return session;
    }

    public BlocksSession getById(UUID sessionId) {
        BlocksSession session = blocksSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocks session not found"));
        log.debug("blocks_session_retrieved session_id={} session_url={}",
                sessionId, session.getSessionUrl() != null ? session.getSessionUrl() : "none");
        return session;
    }

    public List<BlocksSession> getSessionsForIssue(UUID issueId) {
        return blocksSessionRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
    }

    public BlocksSession getActiveSessionForIssue(UUID issueId) {
        return blocksSessionRepository.findActiveByIssueId(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active Blocks session for issue"));
    }

    @Transactional
    public void handleWebhookStateUpdate(String providerSessionId, AIExecutionState newState,
                                         String prUrl, Integer prNumber, String branchName,
                                         String errorMessage, String sessionUrl) {
        BlocksSession session = blocksSessionRepository.findByProviderSessionId(providerSessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No Blocks session with providerSessionId: " + providerSessionId));

        if (session.getExecutionState() == AIExecutionState.DONE
                || session.getExecutionState() == AIExecutionState.CANCELED) {
            log.warn("Ignoring webhook state update for terminal session sessionId={} currentState={}",
                    session.getId(), session.getExecutionState());
            return;
        }

        String oldState = session.getExecutionState().name();
        session.setExecutionState(newState);
        if (prUrl != null) session.setPrUrl(prUrl);
        if (prNumber != null) session.setPrNumber(prNumber);
        if (branchName != null) session.setBranchName(branchName);
        if (errorMessage != null) session.setErrorMessage(errorMessage);
        if (sessionUrl != null && session.getSessionUrl() == null) {
            session.setSessionUrl(sessionUrl);
            log.info("blocks_session_url_set_via_webhook session_id={} provider_session_id={} session_url={}",
                    session.getId(), providerSessionId, sessionUrl);
        }
        session = blocksSessionRepository.save(session);

        Issue issue = session.getIssue();
        issue.setAiAssignmentState(session.getExecutionState().name());
        issueRepository.save(issue);

        ActivityEventType eventType = resolveActivityEventType(session.getExecutionState());
        activityService.record(issue, null, eventType, oldState, session.getExecutionState().name());

        log.info("Blocks session state advanced via webhook sessionId={} from={} to={}",
                session.getId(), oldState, session.getExecutionState());
    }

    private ActivityEventType resolveActivityEventType(AIExecutionState state) {
        return switch (state) {
            case PR_OPENED -> ActivityEventType.AI_PR_OPENED;
            case DONE -> ActivityEventType.AI_COMPLETED;
            case FAILED -> ActivityEventType.AI_FAILED;
            case CANCELED -> ActivityEventType.AI_CANCELED;
            default -> ActivityEventType.AI_STATE_CHANGED;
        };
    }
}
