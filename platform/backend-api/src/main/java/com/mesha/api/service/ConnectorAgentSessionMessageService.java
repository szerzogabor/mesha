package com.mesha.api.service;

import com.mesha.api.model.ConnectorAgentSession;
import com.mesha.api.model.ConnectorAgentSessionMessage;
import com.mesha.api.model.ConnectorAgentSessionStatus;
import com.mesha.api.repository.ConnectorAgentSessionMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ConnectorAgentSessionMessageService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorAgentSessionMessageService.class);

    private static final Set<ConnectorAgentSessionStatus> TERMINAL = EnumSet.of(
        ConnectorAgentSessionStatus.COMPLETED, ConnectorAgentSessionStatus.FAILED, ConnectorAgentSessionStatus.CANCELLED);

    private final ConnectorAgentSessionMessageRepository messageRepository;
    private final ConnectorAgentSessionService connectorAgentSessionService;

    public ConnectorAgentSessionMessageService(ConnectorAgentSessionMessageRepository messageRepository,
                                                ConnectorAgentSessionService connectorAgentSessionService) {
        this.messageRepository = messageRepository;
        this.connectorAgentSessionService = connectorAgentSessionService;
    }

    @Transactional
    public ConnectorAgentSessionMessage addUserMessage(UUID userId, UUID sessionId, String content) {
        ConnectorAgentSession session = connectorAgentSessionService.getOwned(sessionId, userId);
        if (TERMINAL.contains(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot send message to a session in terminal state: " + session.getStatus());
        }

        ConnectorAgentSessionMessage message = new ConnectorAgentSessionMessage();
        message.setSession(session);
        message.setRole("USER");
        message.setContent(content);
        ConnectorAgentSessionMessage saved = messageRepository.save(message);
        log.info("connector_agent_session_user_message_added sessionId={}", sessionId);

        connectorAgentSessionService.resumeFromWaitingForUser(session);

        return saved;
    }

    public List<ConnectorAgentSessionMessage> getMessagesForSession(UUID userId, UUID sessionId) {
        connectorAgentSessionService.getOwned(sessionId, userId);
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public List<ConnectorAgentSessionMessage> fetchPendingForConnector(UUID userId, UUID sessionId) {
        connectorAgentSessionService.getOwned(sessionId, userId);
        List<ConnectorAgentSessionMessage> pending = messageRepository.findBySessionIdAndDeliveredAtIsNullOrderByCreatedAtAsc(sessionId);
        Instant now = Instant.now();
        pending.forEach(m -> m.setDeliveredAt(now));
        return messageRepository.saveAll(pending);
    }
}
