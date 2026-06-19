package com.mesha.api.controller;

import com.mesha.api.dto.ConnectorAgentDto;
import com.mesha.api.dto.RegisterConnectorAgentRequest;
import com.mesha.api.model.ConnectorAgent;
import com.mesha.api.security.ConnectorUserId;
import com.mesha.api.service.ConnectorAgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Called by the Mesha Connector CLI, authenticated with its opaque connector access token. */
@RestController
@RequestMapping("/api/connector/agents")
public class ConnectorAgentController {

    private final ConnectorAgentService connectorAgentService;

    public ConnectorAgentController(ConnectorAgentService connectorAgentService) {
        this.connectorAgentService = connectorAgentService;
    }

    @PostMapping("/register")
    public ResponseEntity<ConnectorAgentDto> register(
            @ConnectorUserId UUID userId,
            @Valid @RequestBody RegisterConnectorAgentRequest req) {
        ConnectorAgent agent = connectorAgentService.register(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ConnectorAgentDto.from(agent, connectorAgentService.getOfflineTimeout()));
    }

    @PostMapping("/{agentId}/heartbeat")
    public ResponseEntity<ConnectorAgentDto> heartbeat(
            @ConnectorUserId UUID userId,
            @PathVariable UUID agentId) {
        ConnectorAgent agent = connectorAgentService.heartbeat(userId, agentId);
        return ResponseEntity.ok(ConnectorAgentDto.from(agent, connectorAgentService.getOfflineTimeout()));
    }
}
