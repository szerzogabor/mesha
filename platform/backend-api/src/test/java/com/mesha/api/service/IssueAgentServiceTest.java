package com.mesha.api.service;

import com.mesha.api.model.*;
import com.mesha.api.repository.AgentDefinitionRepository;
import com.mesha.api.repository.IssueAgentRepository;
import com.mesha.api.repository.IssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IssueAgentServiceTest {

    @Mock private IssueAgentRepository issueAgentRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private AgentDefinitionRepository agentDefinitionRepository;

    private IssueAgentService service;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new IssueAgentService(issueAgentRepository, issueRepository, agentDefinitionRepository);
        workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", UUID.randomUUID());
    }

    private Issue issueInWorkspace(UUID issueId) {
        Project project = new Project();
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        project.setWorkspace(workspace);
        Issue issue = new Issue();
        ReflectionTestUtils.setField(issue, "id", issueId);
        issue.setProject(project);
        return issue;
    }

    private AgentDefinition agentInWorkspace(UUID agentId, boolean active) {
        AgentDefinition agent = new AgentDefinition();
        ReflectionTestUtils.setField(agent, "id", agentId);
        agent.setWorkspace(workspace);
        agent.setActive(active);
        return agent;
    }

    @Test
    void assign_success() {
        UUID issueId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        Issue issue = issueInWorkspace(issueId);
        AgentDefinition agent = agentInWorkspace(agentId, true);

        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(agentDefinitionRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(issueAgentRepository.findByIssueIdAndAgentDefinitionId(issueId, agentId)).thenReturn(Optional.empty());
        when(issueAgentRepository.save(any())).thenAnswer(inv -> {
            IssueAgent ia = inv.getArgument(0);
            ReflectionTestUtils.setField(ia, "id", UUID.randomUUID());
            return ia;
        });

        IssueAgent result = service.assign(issueId, agentId, user);

        assertThat(result.getIssue()).isEqualTo(issue);
        assertThat(result.getAgentDefinition()).isEqualTo(agent);
        assertThat(result.getAssignedBy()).isEqualTo(user);
    }

    @Test
    void assign_inactiveAgent_throws() {
        UUID issueId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        Issue issue = issueInWorkspace(issueId);
        AgentDefinition agent = agentInWorkspace(agentId, false);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(agentDefinitionRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.assign(issueId, agentId, new User()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("inactive");
    }

    @Test
    void assign_alreadyAssigned_returnsExisting() {
        UUID issueId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        Issue issue = issueInWorkspace(issueId);
        AgentDefinition agent = agentInWorkspace(agentId, true);

        IssueAgent existingAssignment = new IssueAgent();
        ReflectionTestUtils.setField(existingAssignment, "id", UUID.randomUUID());
        existingAssignment.setIssue(issue);
        existingAssignment.setAgentDefinition(agent);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(agentDefinitionRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(issueAgentRepository.findByIssueIdAndAgentDefinitionId(issueId, agentId)).thenReturn(Optional.of(existingAssignment));

        IssueAgent result = service.assign(issueId, agentId, new User());

        assertThat(result).isSameAs(existingAssignment);
        verify(issueAgentRepository, never()).save(any());
    }

    @Test
    void assign_crossWorkspace_throws() {
        UUID issueId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        Issue issue = issueInWorkspace(issueId);

        Workspace otherWorkspace = new Workspace();
        ReflectionTestUtils.setField(otherWorkspace, "id", UUID.randomUUID());
        AgentDefinition agent = new AgentDefinition();
        ReflectionTestUtils.setField(agent, "id", agentId);
        agent.setWorkspace(otherWorkspace);
        agent.setActive(true);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(agentDefinitionRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.assign(issueId, agentId, new User()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not belong");
    }

    @Test
    void unassign_success() {
        UUID issueId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        IssueAgent ia = new IssueAgent();
        ReflectionTestUtils.setField(ia, "id", UUID.randomUUID());

        when(issueAgentRepository.findByIssueIdAndAgentDefinitionId(issueId, agentId))
            .thenReturn(Optional.of(ia));

        service.unassign(issueId, agentId);

        verify(issueAgentRepository).delete(ia);
    }

    @Test
    void unassign_notFound_throws() {
        UUID issueId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        when(issueAgentRepository.findByIssueIdAndAgentDefinitionId(issueId, agentId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(issueId, agentId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not found");
    }
}
