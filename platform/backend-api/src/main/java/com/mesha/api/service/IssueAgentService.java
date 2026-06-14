package com.mesha.api.service;

import com.mesha.api.model.AgentDefinition;
import com.mesha.api.model.Issue;
import com.mesha.api.model.IssueAgent;
import com.mesha.api.model.User;
import com.mesha.api.repository.AgentDefinitionRepository;
import com.mesha.api.repository.IssueAgentRepository;
import com.mesha.api.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class IssueAgentService {

    private static final Logger log = LoggerFactory.getLogger(IssueAgentService.class);

    private final IssueAgentRepository issueAgentRepository;
    private final IssueRepository issueRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;

    public IssueAgentService(IssueAgentRepository issueAgentRepository,
                             IssueRepository issueRepository,
                             AgentDefinitionRepository agentDefinitionRepository) {
        this.issueAgentRepository = issueAgentRepository;
        this.issueRepository = issueRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    public List<IssueAgent> listByIssue(UUID issueId) {
        return issueAgentRepository.findAllByIssueIdOrderByAssignedAtDesc(issueId);
    }

    @Transactional
    public IssueAgent assign(UUID issueId, UUID agentDefinitionId, User assignedBy) {
        log.debug("Assigning agent agentId={} to issueId={}", agentDefinitionId, issueId);

        Issue issue = issueRepository.findById(issueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        AgentDefinition agent = agentDefinitionRepository.findById(agentDefinitionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent definition not found"));

        if (!agent.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign an inactive agent");
        }

        if (issueAgentRepository.existsByIssueIdAndAgentDefinitionId(issueId, agentDefinitionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent is already assigned to this issue");
        }

        IssueAgent issueAgent = new IssueAgent();
        issueAgent.setIssue(issue);
        issueAgent.setAgentDefinition(agent);
        issueAgent.setAssignedBy(assignedBy);

        issueAgent = issueAgentRepository.save(issueAgent);
        log.info("Agent assigned issueAgentId={} issueId={} agentId={}", issueAgent.getId(), issueId, agentDefinitionId);
        return issueAgent;
    }

    @Transactional
    public void unassign(UUID issueId, UUID agentDefinitionId) {
        log.debug("Unassigning agent agentId={} from issueId={}", agentDefinitionId, issueId);
        IssueAgent issueAgent = issueAgentRepository.findByIssueIdAndAgentDefinitionId(issueId, agentDefinitionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent assignment not found"));
        issueAgentRepository.delete(issueAgent);
        log.info("Agent unassigned issueId={} agentId={}", issueId, agentDefinitionId);
    }
}
