package com.mesha.api.service;

import com.mesha.api.model.BlocksMessage;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.repository.BlocksMessageRepository;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.worker.blocks.BlocksAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class BlocksMessageService {

    private static final Logger log = LoggerFactory.getLogger(BlocksMessageService.class);

    private final BlocksMessageRepository messageRepository;
    private final BlocksSessionRepository sessionRepository;
    private final BlocksAdapter blocksAdapter;

    public BlocksMessageService(BlocksMessageRepository messageRepository,
                                BlocksSessionRepository sessionRepository,
                                BlocksAdapter blocksAdapter) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.blocksAdapter = blocksAdapter;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BlocksMessage addMessage(UUID sessionId, String message) {
        return addMessage(sessionId, message, "AI");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BlocksMessage addMessage(UUID sessionId, String message, String role) {
        BlocksSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocks session not found"));
        BlocksMessage msg = new BlocksMessage();
        msg.setSession(session);
        msg.setMessage(message);
        msg.setRole(role);
        BlocksMessage saved = messageRepository.save(msg);
        log.debug("blocks_message_recorded sessionId={} role={} message={}", sessionId, role, message);
        return saved;
    }

    @Transactional
    public BlocksMessage addUserMessage(UUID sessionId, String content) {
        BlocksSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocks session not found"));

        if (session.getExecutionState().name().equals("CANCELED") || session.getExecutionState().name().equals("FAILED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot send message to a session in terminal state: " + session.getExecutionState());
        }

        BlocksMessage msg = new BlocksMessage();
        msg.setSession(session);
        msg.setMessage(content);
        msg.setRole("USER");
        BlocksMessage saved = messageRepository.save(msg);
        log.info("blocks_user_message_added sessionId={}", sessionId);

        String providerSessionId = session.getProviderSessionId();
        if (providerSessionId != null && !providerSessionId.isBlank()) {
            try {
                blocksAdapter.sendUserMessage(providerSessionId, content);
            } catch (Exception e) {
                log.warn("blocks_user_message_forward_failed sessionId={} providerSessionId={} error={}",
                        sessionId, providerSessionId, e.getMessage());
            }
        } else {
            log.warn("blocks_user_message_not_forwarded sessionId={} — session not yet dispatched to Blocks", sessionId);
        }

        return saved;
    }

    public List<BlocksMessage> getMessagesForSession(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocks session not found");
        }
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
