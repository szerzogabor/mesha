package com.mesha.api.service;

import com.mesha.api.model.ConnectorAgentSession;
import com.mesha.api.model.ConnectorAgentSessionMessage;
import com.mesha.api.model.ConnectorAgentSessionStatus;
import com.mesha.api.repository.ConnectorAgentSessionMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorAgentSessionMessageServiceTest {

    @Mock private ConnectorAgentSessionMessageRepository messageRepository;
    @Mock private ConnectorAgentSessionService connectorAgentSessionService;

    private ConnectorAgentSessionMessageService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ConnectorAgentSessionMessageService(messageRepository, connectorAgentSessionService);
    }

    private ConnectorAgentSession session(ConnectorAgentSessionStatus status) {
        ConnectorAgentSession session = new ConnectorAgentSession();
        session.setUserId(userId);
        session.setStatus(status);
        return session;
    }

    @Test
    void addUserMessage_persistsAndResumesWaitingSession() {
        ConnectorAgentSession session = session(ConnectorAgentSessionStatus.WAITING_FOR_USER);
        when(connectorAgentSessionService.getOwned(sessionId, userId)).thenReturn(session);
        when(messageRepository.save(any(ConnectorAgentSessionMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ConnectorAgentSessionMessage saved = service.addUserMessage(userId, sessionId, "please continue");

        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.getContent()).isEqualTo("please continue");
        assertThat(saved.getSession()).isEqualTo(session);
        verify(connectorAgentSessionService).resumeFromWaitingForUser(session);
    }

    @Test
    void addUserMessage_terminalSession_throwsConflict() {
        ConnectorAgentSession session = session(ConnectorAgentSessionStatus.COMPLETED);
        when(connectorAgentSessionService.getOwned(sessionId, userId)).thenReturn(session);

        assertThatThrownBy(() -> service.addUserMessage(userId, sessionId, "hello"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getMessagesForSession_returnsOrderedMessages() {
        ConnectorAgentSession session = session(ConnectorAgentSessionStatus.RUNNING);
        when(connectorAgentSessionService.getOwned(sessionId, userId)).thenReturn(session);
        ConnectorAgentSessionMessage message = new ConnectorAgentSessionMessage();
        message.setSession(session);
        message.setRole("AGENT");
        message.setContent("working on it");
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(message));

        List<ConnectorAgentSessionMessage> messages = service.getMessagesForSession(userId, sessionId);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("working on it");
    }

    @Test
    void fetchPendingForConnector_marksMessagesDelivered() {
        ConnectorAgentSession session = session(ConnectorAgentSessionStatus.RUNNING);
        when(connectorAgentSessionService.getOwned(sessionId, userId)).thenReturn(session);
        ConnectorAgentSessionMessage pending = new ConnectorAgentSessionMessage();
        pending.setSession(session);
        pending.setRole("USER");
        pending.setContent("are you done?");
        when(messageRepository.findBySessionIdAndDeliveredAtIsNullOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(pending));

        List<ConnectorAgentSessionMessage> delivered = service.fetchPendingForConnector(userId, sessionId);

        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0).getDeliveredAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }
}
