package com.mesha.api.service;

import com.mesha.api.dto.CreateAgentDefinitionRequest;
import com.mesha.api.dto.UpdateAgentDefinitionRequest;
import com.mesha.api.model.AgentDefinition;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.AgentDefinitionRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AgentDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(AgentDefinitionService.class);

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final WorkspaceRepository workspaceRepository;

    public AgentDefinitionService(AgentDefinitionRepository agentDefinitionRepository,
                                  WorkspaceRepository workspaceRepository) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public List<AgentDefinition> listByWorkspace(UUID workspaceId) {
        long startMs = System.currentTimeMillis();
        List<AgentDefinition> agents = agentDefinitionRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        log.info("Listed agent definitions count={} durationMs={}", agents.size(), System.currentTimeMillis() - startMs);
        return agents;
    }

    public List<AgentDefinition> listActiveByWorkspace(UUID workspaceId) {
        return agentDefinitionRepository.findAllByWorkspaceIdAndActiveTrueOrderByTitleAsc(workspaceId);
    }

    public AgentDefinition getById(UUID workspaceId, UUID agentId) {
        AgentDefinition agent = agentDefinitionRepository.findById(agentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent definition not found"));
        if (!agent.getWorkspace().getId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent definition not found");
        }
        return agent;
    }

    @Transactional
    public AgentDefinition create(UUID workspaceId, CreateAgentDefinitionRequest req) {
        log.debug("Creating agent definition workspaceId={} name={}", workspaceId, req.name());
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        if (agentDefinitionRepository.existsByWorkspaceIdAndName(workspaceId, req.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent name already exists in this workspace");
        }

        AgentDefinition agent = new AgentDefinition();
        agent.setWorkspace(workspace);
        agent.setName(req.name());
        agent.setTitle(req.title());
        agent.setDescription(req.description());
        agent.setProviderType(req.providerType());
        agent.setSystemPrompt(req.systemPrompt());
        agent.setProviderParameters(req.providerParameters() != null ? req.providerParameters() : Map.of());
        agent.setBlocksAgentName(req.blocksAgentName());
        agent.setActive(req.active() != null ? req.active() : true);

        agent = agentDefinitionRepository.save(agent);
        log.info("Agent definition created agentId={} workspaceId={} name={}", agent.getId(), workspaceId, req.name());
        return agent;
    }

    @Transactional
    public AgentDefinition update(UUID workspaceId, UUID agentId, UpdateAgentDefinitionRequest req) {
        log.debug("Updating agent definition agentId={} workspaceId={}", agentId, workspaceId);
        AgentDefinition agent = getById(workspaceId, agentId);

        if (req.name() != null && !req.name().equals(agent.getName())) {
            if (agentDefinitionRepository.existsByWorkspaceIdAndName(workspaceId, req.name())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent name already exists in this workspace");
            }
            agent.setName(req.name());
        }
        if (req.title() != null) agent.setTitle(req.title());
        if (req.description() != null) agent.setDescription(req.description());
        if (req.providerType() != null) agent.setProviderType(req.providerType());
        if (req.systemPrompt() != null) agent.setSystemPrompt(req.systemPrompt());
        if (req.providerParameters() != null) agent.setProviderParameters(req.providerParameters());
        if (req.blocksAgentName() != null) agent.setBlocksAgentName(req.blocksAgentName().isBlank() ? null : req.blocksAgentName());
        if (req.active() != null) agent.setActive(req.active());

        agent = agentDefinitionRepository.save(agent);
        log.info("Agent definition updated agentId={} workspaceId={}", agentId, workspaceId);
        return agent;
    }

    @Transactional
    public void delete(UUID workspaceId, UUID agentId) {
        log.debug("Deleting agent definition agentId={} workspaceId={}", agentId, workspaceId);
        AgentDefinition agent = getById(workspaceId, agentId);
        agentDefinitionRepository.delete(agent);
        log.info("Agent definition deleted agentId={} workspaceId={}", agentId, workspaceId);
    }
}
