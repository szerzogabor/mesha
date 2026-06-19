package com.mesha.api.controller;

import com.mesha.api.dto.ConnectorAgentSessionDto;
import com.mesha.api.dto.CreateConnectorAgentSessionRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
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

    public AgentSessionController(ConnectorAgentSessionService connectorAgentSessionService) {
        this.connectorAgentSessionService = connectorAgentSessionService;
    }

    @PostMapping
    public ResponseEntity<ConnectorAgentSessionDto> create(@CurrentUser User user,
                                                            @Valid @RequestBody CreateConnectorAgentSessionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ConnectorAgentSessionDto.from(connectorAgentSessionService.create(user.getId(), req)));
    }

    @PostMapping("/{sessionId}/enqueue")
    public ResponseEntity<ConnectorAgentSessionDto> enqueue(@CurrentUser User user, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ConnectorAgentSessionDto.from(connectorAgentSessionService.enqueue(user.getId(), sessionId)));
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<ConnectorAgentSessionDto> cancel(@CurrentUser User user, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ConnectorAgentSessionDto.from(connectorAgentSessionService.cancel(user.getId(), sessionId)));
    }

    @GetMapping
    public ResponseEntity<List<ConnectorAgentSessionDto>> list(@CurrentUser User user) {
        List<ConnectorAgentSessionDto> sessions = connectorAgentSessionService.listForUser(user.getId())
            .stream().map(ConnectorAgentSessionDto::from).toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ConnectorAgentSessionDto> get(@CurrentUser User user, @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ConnectorAgentSessionDto.from(connectorAgentSessionService.getOwned(sessionId, user.getId())));
    }
}
