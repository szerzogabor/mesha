package com.mesha.api.service;

import com.mesha.api.model.BlocksMessage;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.repository.BlocksMessageRepository;
import com.mesha.api.repository.BlocksSessionRepository;
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

    public BlocksMessageService(BlocksMessageRepository messageRepository,
                                BlocksSessionRepository sessionRepository) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BlocksMessage addMessage(UUID sessionId, String message) {
        BlocksSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocks session not found"));
        BlocksMessage msg = new BlocksMessage();
        msg.setSession(session);
        msg.setMessage(message);
        BlocksMessage saved = messageRepository.save(msg);
        log.debug("blocks_message_recorded sessionId={} message={}", sessionId, message);
        return saved;
    }

    public List<BlocksMessage> getMessagesForSession(UUID sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocks session not found");
        }
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
