package com.mesha.api.service;

import com.mesha.api.dto.CreateConnectorAgentSessionRequest;
import com.mesha.api.model.ConnectorAgent;
import com.mesha.api.model.ConnectorAgentSession;
import com.mesha.api.model.ConnectorAgentSessionStatus;
import com.mesha.api.model.Issue;
import com.mesha.api.model.WorkspaceRole;
import com.mesha.api.repository.ConnectorAgentRepository;
import com.mesha.api.repository.ConnectorAgentSessionRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.repository.WorkspaceMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.mesha.api.model.ConnectorAgentSessionStatus.*;

@Service
public class ConnectorAgentSessionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorAgentSessionService.class);

    /** Allowed status transitions. Anything not listed as a source key is terminal. */
    private static final Map<ConnectorAgentSessionStatus, Set<ConnectorAgentSessionStatus>> TRANSITIONS = new EnumMap<>(ConnectorAgentSessionStatus.class);
    static {
        TRANSITIONS.put(CREATED, EnumSet.of(QUEUED, CANCELLED));
        TRANSITIONS.put(QUEUED, EnumSet.of(CLAIMED, CANCELLED));
        TRANSITIONS.put(CLAIMED, EnumSet.of(PREPARING, CANCELLED, FAILED));
        TRANSITIONS.put(PREPARING, EnumSet.of(RUNNING, CANCELLED, FAILED));
        TRANSITIONS.put(RUNNING, EnumSet.of(WAITING_FOR_USER, COMPLETED, CANCELLED, FAILED));
        TRANSITIONS.put(WAITING_FOR_USER, EnumSet.of(RUNNING, COMPLETED, CANCELLED, FAILED));
    }

    private final ConnectorAgentSessionRepository connectorAgentSessionRepository;
    private final ConnectorAgentRepository connectorAgentRepository;
    private final IssueRepository issueRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public ConnectorAgentSessionService(ConnectorAgentSessionRepository connectorAgentSessionRepository,
                                        ConnectorAgentRepository connectorAgentRepository,
                                        IssueRepository issueRepository,
                                        WorkspaceMemberRepository workspaceMemberRepository) {
        this.connectorAgentSessionRepository = connectorAgentSessionRepository;
        this.connectorAgentRepository = connectorAgentRepository;
        this.issueRepository = issueRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional
    public ConnectorAgentSession create(UUID userId, CreateConnectorAgentSessionRequest req) {
        Issue issue = issueRepository.findById(req.issueId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
        UUID workspaceId = issue.getProject().getWorkspace().getId();
        boolean isMember = workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndRoleIn(
            workspaceId, userId, List.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.DEVELOPER, WorkspaceRole.VIEWER));
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found");
        }

        ConnectorAgentSession session = new ConnectorAgentSession();
        session.setUserId(userId);
        session.setIssueId(req.issueId());
        if (req.instructions() != null && !req.instructions().isBlank()) {
            session.setInstructions(req.instructions().trim());
        }
        session = connectorAgentSessionRepository.save(session);
        log.info("connector_agent_session_created sessionId={} userId={} issueId={}", session.getId(), userId, req.issueId());
        return session;
    }

    @Transactional
    public ConnectorAgentSession enqueue(UUID userId, UUID sessionId) {
        ConnectorAgentSession session = getOwned(sessionId, userId);
        transition(session, QUEUED);
        session.setQueuedAt(Instant.now());
        return connectorAgentSessionRepository.save(session);
    }

    @Transactional
    public ConnectorAgentSession cancel(UUID userId, UUID sessionId) {
        ConnectorAgentSession session = getOwned(sessionId, userId);
        transition(session, CANCELLED);
        return connectorAgentSessionRepository.save(session);
    }

    /**
     * Atomically claims the oldest queued session owned by {@code userId} for the given agent.
     * Returns empty if no session is currently queued (or another connector poll won the race).
     */
    @Transactional
    public Optional<ConnectorAgentSession> claimNext(UUID userId, UUID agentId) {
        ConnectorAgent agent = connectorAgentRepository.findByIdAndUserId(agentId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        Optional<UUID> nextId = connectorAgentSessionRepository.findNextQueuedIdForUpdate(userId);
        if (nextId.isEmpty()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        int updated = connectorAgentSessionRepository.claim(nextId.get(), agent.getId(), now);
        if (updated == 0) {
            return Optional.empty();
        }

        ConnectorAgentSession session = connectorAgentSessionRepository.findById(nextId.get())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        log.info("connector_agent_session_claimed sessionId={} agentId={} userId={}", session.getId(), agentId, userId);
        return Optional.of(session);
    }

    /**
     * Transitions a session that an agent already claimed. Ownership is enforced via the
     * agent: the caller must own the agent currently assigned to the session.
     */
    @Transactional
    public ConnectorAgentSession updateStatusByAgent(UUID userId, UUID sessionId, ConnectorAgentSessionStatus newStatus, String errorMessage) {
        ConnectorAgentSession session = connectorAgentSessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (session.getAgentId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not claimed by any agent");
        }

        transition(session, newStatus);
        stampTimestamps(session, newStatus);
        if (errorMessage != null) {
            session.setErrorMessage(errorMessage);
        }
        session = connectorAgentSessionRepository.save(session);
        log.info("connector_agent_session_status_updated sessionId={} status={}", sessionId, newStatus);
        return session;
    }

    public List<ConnectorAgentSession> listForUser(UUID userId) {
        return connectorAgentSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public ConnectorAgentSession getOwned(UUID sessionId, UUID userId) {
        return connectorAgentSessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private void transition(ConnectorAgentSession session, ConnectorAgentSessionStatus newStatus) {
        Set<ConnectorAgentSessionStatus> allowed = TRANSITIONS.getOrDefault(session.getStatus(), Set.of());
        if (!allowed.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot transition session from " + session.getStatus() + " to " + newStatus);
        }
        session.setStatus(newStatus);
    }

    private void stampTimestamps(ConnectorAgentSession session, ConnectorAgentSessionStatus newStatus) {
        Instant now = Instant.now();
        if (newStatus == RUNNING && session.getStartedAt() == null) {
            session.setStartedAt(now);
        }
        if ((newStatus == COMPLETED || newStatus == FAILED || newStatus == CANCELLED) && session.getCompletedAt() == null) {
            session.setCompletedAt(now);
        }
    }
}
