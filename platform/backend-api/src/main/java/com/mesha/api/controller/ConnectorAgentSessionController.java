package com.mesha.api.controller;

import com.mesha.api.dto.ClaimConnectorAgentSessionRequest;
import com.mesha.api.dto.ConnectorAgentSessionContextDto;
import com.mesha.api.dto.ConnectorAgentSessionDto;
import com.mesha.api.dto.ConnectorAgentSessionMessageDto;
import com.mesha.api.dto.ReportPullRequestRequest;
import com.mesha.api.dto.UpdateConnectorAgentSessionStatusRequest;
import com.mesha.api.security.ConnectorUserId;
import com.mesha.api.service.ConnectorAgentSessionMessageService;
import com.mesha.api.service.ConnectorAgentSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Called by the Mesha Connector CLI, authenticated with its opaque connector access token. */
@RestController
@RequestMapping("/api/connector/agent-sessions")
public class ConnectorAgentSessionController {

    private final ConnectorAgentSessionService connectorAgentSessionService;
    private final ConnectorAgentSessionMessageService connectorAgentSessionMessageService;

    public ConnectorAgentSessionController(ConnectorAgentSessionService connectorAgentSessionService,
                                            ConnectorAgentSessionMessageService connectorAgentSessionMessageService) {
        this.connectorAgentSessionService = connectorAgentSessionService;
        this.connectorAgentSessionMessageService = connectorAgentSessionMessageService;
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
        var session = connectorAgentSessionService.updateStatusByAgent(
            userId, sessionId, req.status(), req.errorMessage(), req.branchName(), req.workspacePath());
        return ResponseEntity.ok(ConnectorAgentSessionDto.from(session));
    }

    @GetMapping("/{sessionId}/context")
    public ResponseEntity<ConnectorAgentSessionContextDto> context(@ConnectorUserId UUID userId,
                                                                     @PathVariable UUID sessionId) {
        return ResponseEntity.ok(connectorAgentSessionService.getContext(userId, sessionId));
    }

    /** Polled by the connector to fetch and claim follow-up messages queued for a session. */
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ConnectorAgentSessionMessageDto>> fetchPendingMessages(@ConnectorUserId UUID userId,
                                                                                       @PathVariable UUID sessionId) {
        List<ConnectorAgentSessionMessageDto> messages = connectorAgentSessionMessageService
            .fetchPendingForConnector(userId, sessionId)
            .stream().map(ConnectorAgentSessionMessageDto::from).toList();
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{sessionId}/pull-request")
    public ResponseEntity<ConnectorAgentSessionDto> reportPullRequest(@ConnectorUserId UUID userId,
                                                                       @PathVariable UUID sessionId,
                                                                       @Valid @RequestBody ReportPullRequestRequest req) {
        var session = connectorAgentSessionService.reportPullRequest(userId, sessionId, req.githubUrl(), req.title(), req.number());
        return ResponseEntity.ok(connectorAgentSessionService.toDto(session));
    }
}
