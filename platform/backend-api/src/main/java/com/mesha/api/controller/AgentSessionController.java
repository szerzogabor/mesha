package com.mesha.api.controller;

import com.mesha.api.dto.ConnectorAgentSessionDto;
import com.mesha.api.dto.ConnectorAgentSessionMessageDto;
import com.mesha.api.dto.CreateConnectorAgentSessionRequest;
import com.mesha.api.dto.SendMessageRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.ConnectorAgentSessionMessageService;
import com.mesha.api.service.ConnectorAgentSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Called by the Mesha web app (Clerk JWT) to create and manage connector agent sessions. */
@RestController
@RequestMapping("/api/agent-sessions")
public class AgentSessionController {

    private final ConnectorAgentSessionService connectorAgentSessionService;
    private final ConnectorAgentSessionMessageService connectorAgentSessionMessageService;

    public AgentSessionController(ConnectorAgentSessionService connectorAgentSessionService,
                                   ConnectorAgentSessionMessageService connectorAgentSessionMessageService) {
        this.connectorAgentSessionService = connectorAgentSessionService;
        this.connectorAgentSessionMessageService = connectorAgentSessionMessageService;
    }

    @PostMapping
    public ResponseEntity<ConnectorAgentSessionDto> create(@CurrentUser User user,
                                                            @Valid @RequestBody CreateConnectorAgentSessionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(connectorAgentSessionService.toDto(connectorAgentSessionService.create(user.getId(), req)));
    }

    @PostMapping("/{sessionId}/enqueue")
    public ResponseEntity<ConnectorAgentSessionDto> enqueue(@CurrentUser User user, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(connectorAgentSessionService.toDto(connectorAgentSessionService.enqueue(user.getId(), sessionId)));
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<ConnectorAgentSessionDto> cancel(@CurrentUser User user, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(connectorAgentSessionService.toDto(connectorAgentSessionService.cancel(user.getId(), sessionId)));
    }

    @GetMapping
    public ResponseEntity<List<ConnectorAgentSessionDto>> list(@CurrentUser User user) {
        return ResponseEntity.ok(connectorAgentSessionService.toDtos(connectorAgentSessionService.listForUser(user.getId())));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ConnectorAgentSessionDto> get(@CurrentUser User user, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(connectorAgentSessionService.toDto(connectorAgentSessionService.getOwned(sessionId, user.getId())));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ConnectorAgentSessionMessageDto>> getMessages(@CurrentUser User user, @PathVariable UUID sessionId) {
        List<ConnectorAgentSessionMessageDto> messages = connectorAgentSessionMessageService
            .getMessagesForSession(user.getId(), sessionId)
            .stream().map(ConnectorAgentSessionMessageDto::from).toList();
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ConnectorAgentSessionMessageDto> sendMessage(@CurrentUser User user, @PathVariable UUID sessionId,
                                                                        @Valid @RequestBody SendMessageRequest req) {
        var message = connectorAgentSessionMessageService.addUserMessage(user.getId(), sessionId, req.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConnectorAgentSessionMessageDto.from(message));
    }
}
