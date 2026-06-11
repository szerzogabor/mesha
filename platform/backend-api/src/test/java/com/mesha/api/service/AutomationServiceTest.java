package com.mesha.api.service;

import com.mesha.api.dto.CreateAutomationRuleRequest;
import com.mesha.api.model.ActivityEventType;
import com.mesha.api.model.AutomationActionType;
import com.mesha.api.model.AutomationRule;
import com.mesha.api.model.AutomationTriggerType;
import com.mesha.api.model.Issue;
import com.mesha.api.model.Label;
import com.mesha.api.model.Project;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.AutomationRuleRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.repository.LabelRepository;
import com.mesha.api.repository.ProjectRepository;
import com.mesha.api.repository.ProjectStatusRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutomationServiceTest {

    @Mock private AutomationRuleRepository ruleRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectStatusRepository projectStatusRepository;
    @Mock private LabelRepository labelRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private ActivityService activityService;
    @Mock private PlatformTransactionManager transactionManager;

    private AutomationService service;
    private AutoCloseable mocks;

    private Project project;
    private Issue issue;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new AutomationService(ruleRepository, projectRepository, projectStatusRepository,
                labelRepository, issueRepository, activityService, transactionManager);

        projectId = UUID.randomUUID();
        Workspace workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", UUID.randomUUID());
        project = new Project();
        ReflectionTestUtils.setField(project, "id", projectId);
        project.setWorkspace(workspace);

        issue = new Issue();
        ReflectionTestUtils.setField(issue, "id", UUID.randomUUID());
        issue.setProject(project);
        issue.setStatus("TODO");
        // Rules reload the issue inside their own transaction
        when(issueRepository.findById(issue.getId())).thenReturn(Optional.of(issue));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private AutomationRule rule(AutomationActionType actionType, String value) {
        AutomationRule rule = new AutomationRule();
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        rule.setProject(project);
        rule.setTriggerType(AutomationTriggerType.PR_OPENED);
        rule.setActionType(actionType);
        rule.setActionValue(value);
        return rule;
    }

    @Test
    void executeForAppliesSetStatusRule() {
        AutomationRule rule = rule(AutomationActionType.SET_STATUS, "REVIEW");
        when(ruleRepository.findAllByProjectIdAndTriggerTypeAndEnabledTrue(projectId, AutomationTriggerType.PR_OPENED))
                .thenReturn(List.of(rule));
        when(projectStatusRepository.existsByProjectIdAndName(projectId, "REVIEW")).thenReturn(true);

        service.executeFor(AutomationTriggerType.PR_OPENED, issue);

        assertThat(issue.getStatus()).isEqualTo("REVIEW");
        verify(issueRepository).save(issue);
        verify(activityService).record(eq(issue), isNull(), eq(ActivityEventType.STATUS_CHANGED), eq("TODO"), eq("REVIEW"));
    }

    @Test
    void executeForSkipsSetStatusWhenStatusNoLongerExists() {
        AutomationRule rule = rule(AutomationActionType.SET_STATUS, "GONE");
        when(ruleRepository.findAllByProjectIdAndTriggerTypeAndEnabledTrue(projectId, AutomationTriggerType.PR_OPENED))
                .thenReturn(List.of(rule));
        when(projectStatusRepository.existsByProjectIdAndName(projectId, "GONE")).thenReturn(false);

        service.executeFor(AutomationTriggerType.PR_OPENED, issue);

        assertThat(issue.getStatus()).isEqualTo("TODO");
        verify(issueRepository, never()).save(any());
        verifyNoInteractions(activityService);
    }

    @Test
    void executeForAddsLabelOnce() {
        UUID labelId = UUID.randomUUID();
        Label label = new Label();
        ReflectionTestUtils.setField(label, "id", labelId);
        label.setName("needs-review");

        AutomationRule rule = rule(AutomationActionType.ADD_LABEL, labelId.toString());
        when(ruleRepository.findAllByProjectIdAndTriggerTypeAndEnabledTrue(projectId, AutomationTriggerType.PR_OPENED))
                .thenReturn(List.of(rule));
        when(labelRepository.findById(labelId)).thenReturn(Optional.of(label));

        service.executeFor(AutomationTriggerType.PR_OPENED, issue);

        assertThat(issue.getLabels()).containsExactly(label);
        verify(activityService).record(eq(issue), isNull(), eq(ActivityEventType.LABEL_ADDED), isNull(), eq("needs-review"));

        // Second run is a no-op: the label is already on the issue
        service.executeFor(AutomationTriggerType.PR_OPENED, issue);

        assertThat(issue.getLabels()).containsExactly(label);
        verify(issueRepository, times(1)).save(issue);
    }

    @Test
    void executeForDefersExecutionUntilCallerTransactionCommits() {
        AutomationRule rule = rule(AutomationActionType.SET_STATUS, "REVIEW");
        when(ruleRepository.findAllByProjectIdAndTriggerTypeAndEnabledTrue(projectId, AutomationTriggerType.PR_OPENED))
                .thenReturn(List.of(rule));
        when(projectStatusRepository.existsByProjectIdAndName(projectId, "REVIEW")).thenReturn(true);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.executeFor(AutomationTriggerType.PR_OPENED, issue);

            // Nothing happens while the caller's transaction is still open
            assertThat(issue.getStatus()).isEqualTo("TODO");
            verify(issueRepository, never()).save(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(issue.getStatus()).isEqualTo("REVIEW");
            verify(issueRepository).save(issue);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void executeForNeverPropagatesRuleFailures() {
        AutomationRule rule = rule(AutomationActionType.SET_STATUS, "REVIEW");
        when(ruleRepository.findAllByProjectIdAndTriggerTypeAndEnabledTrue(projectId, AutomationTriggerType.PR_OPENED))
                .thenReturn(List.of(rule));
        when(projectStatusRepository.existsByProjectIdAndName(projectId, "REVIEW")).thenReturn(true);
        when(issueRepository.save(any())).thenThrow(new RuntimeException("db down"));

        service.executeFor(AutomationTriggerType.PR_OPENED, issue);
    }

    @Test
    void createRejectsUnknownStatus() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectStatusRepository.existsByProjectIdAndName(projectId, "NOPE")).thenReturn(false);

        CreateAutomationRuleRequest req = new CreateAutomationRuleRequest(
                AutomationTriggerType.PR_OPENED, AutomationActionType.SET_STATUS, "NOPE");

        assertThatThrownBy(() -> service.create(projectId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown status");
    }

    @Test
    void createRejectsLabelFromOtherWorkspace() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        UUID labelId = UUID.randomUUID();
        Workspace otherWorkspace = new Workspace();
        ReflectionTestUtils.setField(otherWorkspace, "id", UUID.randomUUID());
        Label label = new Label();
        ReflectionTestUtils.setField(label, "id", labelId);
        label.setWorkspace(otherWorkspace);
        when(labelRepository.findById(labelId)).thenReturn(Optional.of(label));

        CreateAutomationRuleRequest req = new CreateAutomationRuleRequest(
                AutomationTriggerType.PR_MERGED, AutomationActionType.ADD_LABEL, labelId.toString());

        assertThatThrownBy(() -> service.create(projectId, req, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown label");
    }
}
