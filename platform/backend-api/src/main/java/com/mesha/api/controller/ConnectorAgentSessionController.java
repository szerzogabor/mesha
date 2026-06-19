package com.mesha.api.controller;

import com.mesha.api.dto.ClaimConnectorAgentSessionRequest;
import com.mesha.api.dto.ConnectorAgentSessionDto;
import com.mesha.api.dto.UpdateConnectorAgentSessionStatusRequest;
import com.mesha.api.security.ConnectorUserId;
import com.mesha.api.service.ConnectorAgentSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Called by the Mesha Connector CLI, authenticated with its opaque connector access token. */
@RestController
@RequestMapping("/api/connector/agent-sessions")
public class ConnectorAgentSessionController {

    private final ConnectorAgentSessionService connectorAgentSessionService;

    public ConnectorAgentSessionController(ConnectorAgentSessionService connectorAgentSessionService) {
        this.connectorAgentSessionService = connectorAgentSessionService;
    }

    @PostMapping("/claim")
    public ResponseEntity<ConnectorAgentSessionDto> claim(@ConnectorUserId UUID userId,
                                                          @Valid @RequestBody ClaimConnectorAgentSessionRequest req) {
        return connectorAgentSessionService.claimNext(userId, req.agentId())
            .map(session -> ResponseEntity.ok(ConnectorAgentSessionDto.from(session)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{sessionId}/status")
    public ResponseEntity<ConnectorAgentSessionDto> updateStatus(@ConnectorUserId UUID userId,
                                                                  @PathVariable UUID sessionId,
                                                                  @Valid @RequestBody UpdateConnectorAgentSessionStatusRequest req) {
        var session = connectorAgentSessionService.updateStatusByAgent(userId, sessionId, req.status(), req.errorMessage());
        return ResponseEntity.ok(ConnectorAgentSessionDto.from(session));
    }
}
