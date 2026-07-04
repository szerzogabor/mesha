package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.model.AutomationActionType;
import com.mesha.api.model.AutomationRule;
import com.mesha.api.model.AutomationRuleAction;
import com.mesha.api.model.AutomationTriggerType;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.GitHubPullRequest;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Issue;
import com.mesha.api.model.Project;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.AutomationRuleRepository;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
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
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * End-to-end regression coverage for the {@code PR_MERGED} automation trigger.
 *
 * <p>The existing {@link GitHubPullRequestServiceTest} mocks {@link AutomationService}, so it only
 * proves {@code executeFor(PR_MERGED, issue)} is <em>called</em> — it never proves the configured
 * action actually <em>runs</em>. These tests wire a <b>real</b> {@link AutomationService} together
 * with a real {@link GitHubPullRequestService} and assert that merging a PR moves the linked ticket
 * to the rule's target status, exercising trigger resolution, issue resolution, the transactional
 * {@code afterCommit} deferral, rule lookup and the {@code SET_STATUS} action as one flow — for both
 * the webhook and the polling-sync entry points, and for both title- and Blocks-session-linked PRs.
 */
class GitHubPrMergedAutomationTest {

    @Mock private GitHubPullRequestRepository prRepo;
    @Mock private GitHubRepositoryRepository repositoryRepo;
    @Mock private GitHubInstallationRepository installationRepo;
    @Mock private BlocksSessionRepository blocksSessionRepo;
    @Mock private IssueRepository issueRepository;
    @Mock private GitHubAppService appService;

    @Mock private AutomationRuleRepository ruleRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectStatusRepository projectStatusRepository;
    @Mock private LabelRepository labelRepository;
    @Mock private ActivityService activityService;
    @Mock private IssueSseService issueSseService;
    @Mock private PushNotificationService pushNotificationService;
    @Mock private PlatformTransactionManager transactionManager;

    private GitHubPullRequestService prService;
    private AutoCloseable mocks;
    private ObjectMapper objectMapper;

    private Workspace workspace;
    private Project project;
    private Issue issue;
    private GitHubRepository repo;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        AutomationService automationService = new AutomationService(ruleRepository, projectRepository,
                projectStatusRepository, labelRepository, issueRepository, activityService, issueSseService,
                pushNotificationService, transactionManager);

        prService = new GitHubPullRequestService(prRepo, repositoryRepo, installationRepo,
                blocksSessionRepo, issueRepository, appService, automationService, objectMapper);

        projectId = UUID.randomUUID();
        workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", UUID.randomUUID());

        project = new Project();
        ReflectionTestUtils.setField(project, "id", projectId);
        project.setKey("TP");
        project.setWorkspace(workspace);

        issue = new Issue();
        ReflectionTestUtils.setField(issue, "id", UUID.randomUUID());
        issue.setProject(project);
        issue.setNumber(84);
        issue.setStatus("IN_REVIEW");

        repo = new GitHubRepository();
        ReflectionTestUtils.setField(repo, "id", UUID.randomUUID());
        repo.setWorkspace(workspace);

        when(repositoryRepo.findByFullName("owner/repo")).thenReturn(Optional.of(repo));
        when(prRepo.findFirstByRepositoryIdAndGithubPrNumberOrderByUpdatedAtDesc(any(), eq(1)))
                .thenReturn(Optional.empty());
        when(prRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(blocksSessionRepo.findFirstByBranchName(any())).thenReturn(Optional.empty());
        when(blocksSessionRepo.findSessionsByProjectKeyAndIssueNumber(any(), any(), any())).thenReturn(List.of());
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));
        when(issueRepository.findById(issue.getId())).thenReturn(Optional.of(issue));

        // "When a PR is merged, move the ticket to DONE."
        AutomationRule rule = new AutomationRule();
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        rule.setProject(project);
        rule.setTriggerType(AutomationTriggerType.PR_MERGED);
        AutomationRuleAction action = new AutomationRuleAction();
        ReflectionTestUtils.setField(action, "id", UUID.randomUUID());
        action.setRule(rule);
        action.setActionType(AutomationActionType.SET_STATUS);
        action.setActionValue("DONE");
        rule.getActions().add(action);

        when(ruleRepository.findEnabledByProjectIdAndTriggerTypeWithActions(projectId, AutomationTriggerType.PR_MERGED))
                .thenReturn(List.of(rule));
        when(projectStatusRepository.existsByProjectIdAndName(projectId, "DONE")).thenReturn(true);

        // Let the rule's REQUIRES_NEW TransactionTemplate actually invoke its callback.
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        mocks.close();
    }

    @Test
    void mergedPrWebhookMovesTicketToTargetStatus() throws Exception {
        runInTransaction(() -> prService.handlePullRequestEvent(mergedWebhook()));
        assertThat(issue.getStatus()).isEqualTo("DONE");
    }

    @Test
    void mergedPrWebhookDefersUntilTransactionCommits() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        prService.handlePullRequestEvent(mergedWebhook());
        // The automation must not run while the webhook's own transaction is still open.
        assertThat(issue.getStatus()).isEqualTo("IN_REVIEW");

        commit();
        assertThat(issue.getStatus()).isEqualTo("DONE");
    }

    @Test
    void mergedPrWebhookMovesTicketWhenLinkedViaBlocksSession() throws Exception {
        BlocksSession session = new BlocksSession();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        session.setIssue(issue);

        GitHubPullRequest existingPr = new GitHubPullRequest();
        ReflectionTestUtils.setField(existingPr, "id", UUID.randomUUID());
        existingPr.setRepository(repo);
        existingPr.setGithubPrNumber(1);
        existingPr.setState("open");
        existingPr.setBlocksSession(session);
        when(prRepo.findFirstByRepositoryIdAndGithubPrNumberOrderByUpdatedAtDesc(any(), eq(1)))
                .thenReturn(Optional.of(existingPr));

        runInTransaction(() -> prService.handlePullRequestEvent(mergedWebhook()));
        assertThat(issue.getStatus()).isEqualTo("DONE");
    }

    @Test
    void mergedPrDiscoveredByPollingSyncMovesTicketToTargetStatus() throws Exception {
        GitHubPullRequest prior = new GitHubPullRequest();
        prior.setRepository(repo);
        prior.setGithubPrNumber(1);
        prior.setState("open");
        Map<Integer, GitHubPullRequest> existing = new HashMap<>();
        existing.put(1, prior);

        JsonNode node = objectMapper.readTree("""
                {
                  "number": 1, "title": "TP-84: some feature", "state": "closed",
                  "user": { "login": "dev", "avatar_url": "https://example.com/a" },
                  "head": { "ref": "feature/TP-84" }, "base": { "ref": "main" },
                  "html_url": "https://github.com/owner/repo/pull/1", "draft": false, "commits": 1,
                  "created_at": "2024-01-01T00:00:00Z",
                  "merged_at": "2024-06-01T12:00:00Z", "closed_at": "2024-06-01T12:00:00Z"
                }
                """);

        runInTransaction(() -> prService.processSyncedPullRequest(
                repo, node, existing, Instant.parse("2024-05-01T00:00:00Z")));
        assertThat(issue.getStatus()).isEqualTo("DONE");
    }

    @Test
    void mergedPrStatusChangeSurvivesPushNotificationFailure() throws Exception {
        // A broken/misconfigured notifier must not roll back the automation's status change.
        doThrow(new RuntimeException("FCM unavailable"))
                .when(pushNotificationService).notifyStatusChanged(any());

        runInTransaction(() -> prService.handlePullRequestEvent(mergedWebhook()));
        assertThat(issue.getStatus()).isEqualTo("DONE");
    }

    @Test
    void closedButNotMergedPrDoesNotTriggerMergedRule() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "action": "closed",
                  "repository": { "full_name": "owner/repo" },
                  "pull_request": {
                    "number": 1, "title": "TP-84: some feature", "state": "closed",
                    "user": { "login": "dev", "avatar_url": "https://example.com/a" },
                    "head": { "ref": "feature/TP-84" }, "base": { "ref": "main" },
                    "html_url": "https://github.com/owner/repo/pull/1", "draft": false, "commits": 1,
                    "merged_at": null, "closed_at": "2024-06-01T12:00:00Z"
                  }
                }
                """);

        runInTransaction(() -> prService.handlePullRequestEvent(payload));
        // A close-without-merge is PR_CLOSED, not PR_MERGED — the merged rule must stay dormant.
        assertThat(issue.getStatus()).isEqualTo("IN_REVIEW");
    }

    // --- helpers ---

    private JsonNode mergedWebhook() throws Exception {
        return objectMapper.readTree("""
                {
                  "action": "closed",
                  "repository": { "full_name": "owner/repo" },
                  "pull_request": {
                    "number": 1, "title": "TP-84: some feature", "state": "closed",
                    "user": { "login": "dev", "avatar_url": "https://example.com/a" },
                    "head": { "ref": "feature/TP-84" }, "base": { "ref": "main" },
                    "html_url": "https://github.com/owner/repo/pull/1", "draft": false, "commits": 1,
                    "merged_at": "2024-06-01T12:00:00Z", "closed_at": "2024-06-01T12:00:00Z"
                  }
                }
                """);
    }

    /** Runs the action inside a simulated caller transaction, then fires afterCommit. */
    private void runInTransaction(ThrowingRunnable action) throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        action.run();
        commit();
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
