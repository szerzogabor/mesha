package com.mesha.api.service;

import com.mesha.api.dto.ConnectorAgentSessionContextDto;
import com.mesha.api.model.Comment;
import com.mesha.api.model.ConnectorAgentSession;
import com.mesha.api.model.ConnectorAgentSessionStatus;
import com.mesha.api.model.GitHubRepository;
import com.mesha.api.model.Issue;
import com.mesha.api.model.IssueLink;
import com.mesha.api.model.IssueLinkType;
import com.mesha.api.model.IssuePriority;
import com.mesha.api.model.Project;
import com.mesha.api.model.User;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.CommentRepository;
import com.mesha.api.repository.ConnectorAgentRepository;
import com.mesha.api.repository.ConnectorAgentSessionRepository;
import com.mesha.api.repository.GitHubRepositoryRepository;
import com.mesha.api.repository.IssueLinkRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ConnectorAgentSessionServiceTest {

    @Mock private ConnectorAgentSessionRepository connectorAgentSessionRepository;
    @Mock private ConnectorAgentRepository connectorAgentRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private IssueLinkRepository issueLinkRepository;
    @Mock private GitHubRepositoryRepository gitHubRepositoryRepository;

    private ConnectorAgentSessionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID issueId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ConnectorAgentSessionService(connectorAgentSessionRepository, connectorAgentRepository,
            issueRepository, workspaceMemberRepository, commentRepository, issueLinkRepository, gitHubRepositoryRepository);
    }

    private ConnectorAgentSession sessionWithIssue() {
        ConnectorAgentSession session = new ConnectorAgentSession();
        session.setUserId(userId);
        session.setIssueId(issueId);
        session.setAgentId(UUID.randomUUID());
        session.setStatus(ConnectorAgentSessionStatus.CLAIMED);
        return session;
    }

    private Issue issue(Workspace workspace) {
        Project project = new Project();
        project.setKey("MES");
        project.setWorkspace(workspace);

        Issue issue = new Issue();
        issue.setProject(project);
        issue.setTitle("Implement Connector Task Polling");
        issue.setDescription("Allow connector to poll and claim queued sessions.");
        issue.setNumber(123);
        issue.setPriority(IssuePriority.HIGH);
        return issue;
    }

    @Test
    void getContext_assemblesIssueCommentsLinksAndRepository() {
        Workspace workspace = new Workspace();
        UUID workspaceId = UUID.randomUUID();
        setId(workspace, workspaceId);
        Issue issue = issue(workspace);
        setId(issue, issueId);

        ConnectorAgentSession session = sessionWithIssue();
        when(connectorAgentSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        User author = new User();
        author.setName("Alice");
        Comment comment = new Comment();
        comment.setAuthor(author);
        comment.setBody("Looks good so far.");
        when(commentRepository.findAllByIssueId(issueId)).thenReturn(List.of(comment));

        Issue related = issue(workspace);
        setId(related, UUID.randomUUID());
        related.setNumber(124);
        related.setTitle("Related ticket");
        IssueLink link = new IssueLink();
        link.setSourceIssue(issue);
        link.setTargetIssue(related);
        link.setLinkType(IssueLinkType.DEPENDS_ON);
        when(issueLinkRepository.findAllByIssueId(issueId)).thenReturn(List.of(link));

        GitHubRepository repo = new GitHubRepository();
        repo.setFullName("acme/widgets");
        repo.setHtmlUrl("https://github.com/acme/widgets");
        repo.setDefaultBranch("main");
        when(gitHubRepositoryRepository.findAllByWorkspaceIdAndConnectedTrue(workspaceId)).thenReturn(List.of(repo));

        ConnectorAgentSessionContextDto context = service.getContext(userId, sessionId);

        assertThat(context.issueIdentifier()).isEqualTo("MES-123");
        assertThat(context.issueTitle()).isEqualTo("Implement Connector Task Polling");
        assertThat(context.comments()).hasSize(1);
        assertThat(context.comments().get(0).author()).isEqualTo("Alice");
        assertThat(context.relatedIssues()).hasSize(1);
        assertThat(context.relatedIssues().get(0).identifier()).isEqualTo("MES-124");
        assertThat(context.repository()).isNotNull();
        assertThat(context.repository().cloneUrl()).isEqualTo("https://github.com/acme/widgets.git");
        assertThat(context.repository().defaultBranch()).isEqualTo("main");
    }

    @Test
    void getContext_sessionWithoutIssue_throwsConflict() {
        ConnectorAgentSession session = sessionWithIssue();
        session.setIssueId(null);
        when(connectorAgentSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.getContext(userId, sessionId)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateStatusByAgent_persistsBranchNameAndWorkspacePath() {
        ConnectorAgentSession session = sessionWithIssue();
        session.setStatus(ConnectorAgentSessionStatus.CLAIMED);
        when(connectorAgentSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(connectorAgentSessionRepository.save(any(ConnectorAgentSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ConnectorAgentSession updated = service.updateStatusByAgent(
            userId, sessionId, ConnectorAgentSessionStatus.PREPARING, null, "feature/MES-123", "/home/user/mesha-workspaces/MES-123");

        assertThat(updated.getBranchName()).isEqualTo("feature/MES-123");
        assertThat(updated.getWorkspacePath()).isEqualTo("/home/user/mesha-workspaces/MES-123");
        assertThat(updated.getStatus()).isEqualTo(ConnectorAgentSessionStatus.PREPARING);
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
