package com.mesha.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.model.AutomationTriggerType;
import com.mesha.api.model.BlocksSession;
import com.mesha.api.model.GitHubPullRequest;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Issue;
import com.mesha.api.model.Project;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.BlocksSessionRepository;
import com.mesha.api.repository.GitHubInstallationRepository;
import com.mesha.api.repository.GitHubPullRequestRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.IssueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GitHubPullRequestServiceTest {

    @Mock private GitHubPullRequestRepository prRepo;
    @Mock private GitHubRepositoryRepository repositoryRepo;
    @Mock private GitHubInstallationRepository installationRepo;
    @Mock private BlocksSessionRepository blocksSessionRepo;
    @Mock private IssueRepository issueRepository;
    @Mock private GitHubAppService appService;
    @Mock private AutomationService automationService;

    private GitHubPullRequestService service;
    private AutoCloseable mocks;
    private ObjectMapper objectMapper;

    private Workspace workspace;
    private Project project;
    private Issue issue;
    private GitHubRepository repo;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        service = new GitHubPullRequestService(prRepo, repositoryRepo, installationRepo,
                blocksSessionRepo, issueRepository, appService, automationService, objectMapper);

        workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", UUID.randomUUID());

        project = new Project();
        ReflectionTestUtils.setField(project, "id", UUID.randomUUID());
        project.setKey("TP");
        project.setWorkspace(workspace);

        issue = new Issue();
        ReflectionTestUtils.setField(issue, "id", UUID.randomUUID());
        issue.setProject(project);
        issue.setNumber(84);

        repo = new GitHubRepository();
        ReflectionTestUtils.setField(repo, "id", UUID.randomUUID());
        repo.setWorkspace(workspace);

        when(repositoryRepo.findByFullName("owner/repo")).thenReturn(Optional.of(repo));
        when(prRepo.findFirstByRepositoryIdAndGithubPrNumberOrderByUpdatedAtDesc(any(), eq(1)))
                .thenReturn(Optional.empty());
        when(prRepo.save(any(GitHubPullRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(blocksSessionRepo.findFirstByBranchName(any())).thenReturn(Optional.empty());
        when(blocksSessionRepo.findSessionsByProjectKeyAndIssueNumber(any(), any(), any()))
                .thenReturn(java.util.List.of());
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void firesAutomationViaBlocksSessionWhenLinked() throws Exception {
        BlocksSession session = new BlocksSession();
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        session.setIssue(issue);

        GitHubPullRequest existingPr = new GitHubPullRequest();
        existingPr.setBlocksSession(session);
        existingPr.setRepository(repo);

        when(prRepo.findFirstByRepositoryIdAndGithubPrNumberOrderByUpdatedAtDesc(any(), eq(1)))
                .thenReturn(Optional.of(existingPr));

        JsonNode payload = openedPayload("TP-84: some feature", "feature/TP-84");
        service.handlePullRequestEvent(payload);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_OPENED), eq(issue));
    }

    @Test
    void firesAutomationViaDirectIssueLookupWhenNoBlocksSession() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));

        JsonNode payload = openedPayload("TP-84: some feature", "feature/TP-84");
        service.handlePullRequestEvent(payload);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_OPENED), eq(issue));
    }

    @Test
    void doesNotFireAutomationWhenIdentifierFoundButIssueDoesNotExist() throws Exception {
        // issueRepository.findByWorkspaceAndProjectKeyAndNumber returns empty by default (setUp)
        JsonNode payload = openedPayload("TP-99: pr for unknown ticket", "feature/TP-99");
        service.handlePullRequestEvent(payload);

        verifyNoInteractions(automationService);
    }

    @Test
    void doesNotFireAutomationWhenTitleHasNoIdentifier() throws Exception {
        JsonNode payload = openedPayload("refactor some things", "feature/random");
        service.handlePullRequestEvent(payload);

        verifyNoInteractions(automationService);
        verify(issueRepository, never()).findByWorkspaceAndProjectKeyAndNumber(any(), any(), any());
    }

    @Test
    void firesAutomationForMergedPr() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));

        JsonNode payload = closedMergedPayload("TP-84: some feature", "feature/TP-84");
        service.handlePullRequestEvent(payload);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_MERGED), eq(issue));
    }

    @Test
    void firesAutomationForClosedNotMergedPr() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));

        JsonNode payload = closedNotMergedPayload("TP-84: some feature", "feature/TP-84");
        service.handlePullRequestEvent(payload);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_CLOSED), eq(issue));
    }

    @Test
    void skipsAutomationForUnrecognizedAction() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(any(), any(), any()))
                .thenReturn(Optional.of(issue));

        JsonNode payload = buildPayload("synchronize", "TP-84: some feature", "feature/TP-84", false);
        service.handlePullRequestEvent(payload);

        verifyNoInteractions(automationService);
    }

    @Test
    void skipsWebhookForUntrackedRepository() throws Exception {
        when(repositoryRepo.findByFullName("owner/repo")).thenReturn(Optional.empty());

        JsonNode payload = openedPayload("TP-84: some feature", "feature/TP-84");
        service.handlePullRequestEvent(payload);

        verifyNoInteractions(automationService);
        verify(prRepo, never()).save(any());
    }

    @Test
    void picksFirstMatchingIdentifierFromTitle() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));

        // Title has two identifiers; first one (TP-84) should match
        JsonNode payload = openedPayload("TP-84: fix plus JIRA-999 reference", "feature/TP-84");
        service.handlePullRequestEvent(payload);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_OPENED), eq(issue));
        // Should not look up JIRA-999 because TP-84 was found first
        verify(issueRepository, times(1)).findByWorkspaceAndProjectKeyAndNumber(any(), any(), any());
    }

    // --- polling sync transition path ---

    private static final Instant LAST_SYNC = Instant.parse("2024-06-01T00:00:00Z");
    private static final String BEFORE_SYNC = "2024-01-01T00:00:00Z";
    private static final String AFTER_SYNC = "2024-06-01T12:00:00Z";

    private GitHubPullRequest priorPr(String state, Instant mergedAt) {
        GitHubPullRequest prior = new GitHubPullRequest();
        prior.setRepository(repo);
        prior.setGithubPrNumber(1);
        prior.setState(state);
        prior.setMergedAt(mergedAt);
        return prior;
    }

    private java.util.Map<Integer, GitHubPullRequest> mapOf(GitHubPullRequest pr) {
        java.util.Map<Integer, GitHubPullRequest> existing = new java.util.HashMap<>();
        existing.put(pr.getGithubPrNumber(), pr);
        return existing;
    }

    @Test
    void syncFiresMergedAutomationWhenOpenPrBecomesMerged() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));

        JsonNode node = prObjectNode("TP-84: some feature", "feature/TP-84", "closed", BEFORE_SYNC, AFTER_SYNC, AFTER_SYNC);
        service.processSyncedPullRequest(repo, node, mapOf(priorPr("open", null)), LAST_SYNC);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_MERGED), eq(issue));
    }

    @Test
    void syncFiresClosedAutomationWhenOpenPrBecomesClosedWithoutMerge() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));

        JsonNode node = prObjectNode("TP-84: some feature", "feature/TP-84", "closed", BEFORE_SYNC, null, AFTER_SYNC);
        service.processSyncedPullRequest(repo, node, mapOf(priorPr("open", null)), LAST_SYNC);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_CLOSED), eq(issue));
    }

    @Test
    void syncFiresOpenedAutomationForNewlyOpenedPrAfterInitialSync() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));

        JsonNode node = prObjectNode("TP-84: some feature", "feature/TP-84", "open", AFTER_SYNC, null, null);
        service.processSyncedPullRequest(repo, node, new java.util.HashMap<>(), LAST_SYNC);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_OPENED), eq(issue));
    }

    @Test
    void syncFiresMergedForPrOpenedAndMergedWithinOneInterval() throws Exception {
        when(issueRepository.findByWorkspaceAndProjectKeyAndNumber(workspace.getId(), "TP", 84))
                .thenReturn(Optional.of(issue));

        // Never seen before (isNew) but merged since the previous sync — the whole lifecycle
        // happened within one polling interval and no webhook was delivered.
        JsonNode node = prObjectNode("TP-84: some feature", "feature/TP-84", "closed", AFTER_SYNC, AFTER_SYNC, AFTER_SYNC);
        service.processSyncedPullRequest(repo, node, new java.util.HashMap<>(), LAST_SYNC);

        verify(automationService).executeFor(eq(AutomationTriggerType.PR_MERGED), eq(issue));
    }

    @Test
    void syncDoesNotFireForOldTerminalPrReenteringWindow() throws Exception {
        // Newly seen by us, but merged long before the previous sync (e.g. re-entered the
        // most-recently-updated page after a late comment). Must not retroactively fire.
        JsonNode node = prObjectNode("TP-84: some feature", "feature/TP-84", "closed", BEFORE_SYNC, BEFORE_SYNC, BEFORE_SYNC);
        service.processSyncedPullRequest(repo, node, new java.util.HashMap<>(), LAST_SYNC);

        verifyNoInteractions(automationService);
    }

    @Test
    void syncDoesNotFireOpenedForPreExistingBacklogOnInitialSync() throws Exception {
        JsonNode node = prObjectNode("TP-84: some feature", "feature/TP-84", "open", BEFORE_SYNC, null, null);
        service.processSyncedPullRequest(repo, node, new java.util.HashMap<>(), null);

        verifyNoInteractions(automationService);
    }

    @Test
    void syncDoesNotReFireWhenAlreadyMerged() throws Exception {
        GitHubPullRequest prior = priorPr("closed", Instant.parse(BEFORE_SYNC));

        JsonNode node = prObjectNode("TP-84: some feature", "feature/TP-84", "closed", BEFORE_SYNC, AFTER_SYNC, AFTER_SYNC);
        service.processSyncedPullRequest(repo, node, mapOf(prior), LAST_SYNC);

        verifyNoInteractions(automationService);
    }

    @Test
    void syncDoesNotFireWhenOpenPrStaysOpen() throws Exception {
        JsonNode node = prObjectNode("TP-84: some feature", "feature/TP-84", "open", BEFORE_SYNC, null, null);
        service.processSyncedPullRequest(repo, node, mapOf(priorPr("open", null)), LAST_SYNC);

        verifyNoInteractions(automationService);
    }

    // --- helpers ---

    private JsonNode prObjectNode(String title, String branch, String state,
            String createdAt, String mergedAt, String closedAt) throws Exception {
        String json = """
                {
                  "number": 1,
                  "title": "%s",
                  "body": null,
                  "state": "%s",
                  "user": { "login": "dev", "avatar_url": "https://example.com/avatar" },
                  "head": { "ref": "%s" },
                  "base": { "ref": "main" },
                  "html_url": "https://github.com/owner/repo/pull/1",
                  "draft": false,
                  "commits": 1,
                  "created_at": %s,
                  "merged_at": %s,
                  "closed_at": %s
                }
                """.formatted(title, state, branch, jsonOrNull(createdAt), jsonOrNull(mergedAt), jsonOrNull(closedAt));
        return objectMapper.readTree(json);
    }

    private static String jsonOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private JsonNode openedPayload(String title, String branch) throws Exception {
        return buildPayload("opened", title, branch, false);
    }

    private JsonNode closedMergedPayload(String title, String branch) throws Exception {
        return buildPayload("closed", title, branch, true);
    }

    private JsonNode closedNotMergedPayload(String title, String branch) throws Exception {
        return buildPayload("closed", title, branch, false);
    }

    private JsonNode buildPayload(String action, String title, String branch, boolean merged) throws Exception {
        String mergedAt = merged ? "\"2024-01-01T00:00:00Z\"" : "null";
        String json = """
                {
                  "action": "%s",
                  "repository": { "full_name": "owner/repo" },
                  "pull_request": {
                    "number": 1,
                    "title": "%s",
                    "body": null,
                    "state": "%s",
                    "user": { "login": "dev", "avatar_url": "https://example.com/avatar" },
                    "head": { "ref": "%s" },
                    "base": { "ref": "main" },
                    "html_url": "https://github.com/owner/repo/pull/1",
                    "draft": false,
                    "commits": 1,
                    "merged_at": %s,
                    "closed_at": null
                  }
                }
                """.formatted(action, title, merged || action.equals("closed") ? "closed" : "open", branch, mergedAt);
        return objectMapper.readTree(json);
    }
}
