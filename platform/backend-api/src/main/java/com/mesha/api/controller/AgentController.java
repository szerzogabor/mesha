package com.mesha.api.controller;

import com.mesha.api.dto.ConnectorAgentDto;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.ConnectorAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Called by the Mesha web app (Clerk JWT) to list the current user's registered connector agents. */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final ConnectorAgentService connectorAgentService;

    public AgentController(ConnectorAgentService connectorAgentService) {
        this.connectorAgentService = connectorAgentService;
    }

    private ConnectorAgentDto toDto(com.mesha.api.model.ConnectorAgent agent) {
        return ConnectorAgentDto.from(agent, connectorAgentService.getOfflineTimeout());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ConnectorAgentDto>> list(@CurrentUser User user) {
        List<ConnectorAgentDto> agents = connectorAgentService.listForUser(user.getId())
            .stream().map(this::toDto).toList();
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/{agentId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ConnectorAgentDto> get(@CurrentUser User user, @PathVariable UUID agentId) {
        return ResponseEntity.ok(toDto(connectorAgentService.getForUser(agentId, user.getId())));
    }
}
