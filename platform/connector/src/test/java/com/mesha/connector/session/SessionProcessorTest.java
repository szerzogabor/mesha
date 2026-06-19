package com.mesha.connector.session;

import com.mesha.connector.context.SessionContextBuilder;
import com.mesha.connector.git.GitWorkspacePreparer;
import com.mesha.connector.session.dto.ClaimedSessionResponse;
import com.mesha.connector.session.dto.SessionContextResponse;
import com.mesha.connector.session.dto.SessionContextResponse.RepositorySummary;
import com.mesha.connector.workspace.WorkspaceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionProcessorTest {

    @Mock private ConnectorAgentSessionClient client;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private GitWorkspacePreparer gitWorkspacePreparer;
    @Mock private SessionContextBuilder contextBuilder;

    @TempDir
    Path tempDir;

    private SessionProcessor processor;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new SessionProcessor(client, workspaceManager, gitWorkspacePreparer, contextBuilder);
        sessionId = UUID.randomUUID();
    }

    @Test
    void process_happyPath_marksPreparingThenRunningAndWritesTaskFile() throws IOException {
        ClaimedSessionResponse claimed = claimedSession();
        SessionContextResponse context = contextFor("MES-123", new RepositorySummary("org/repo",
                "https://github.com/org/repo", "https://github.com/org/repo.git", "main"));
        when(client.fetchContext(sessionId)).thenReturn(context);
        when(workspaceManager.prepare("MES-123")).thenReturn(tempDir);
        when(contextBuilder.build(context)).thenReturn("# task brief");

        processor.process(claimed);

        verify(client).updateStatus(sessionId, ConnectorSessionStatus.PREPARING, null, null, null);
        verify(gitWorkspacePreparer).prepare(tempDir, "https://github.com/org/repo.git", "main", "feature/MES-123");
        verify(client).updateStatus(sessionId, ConnectorSessionStatus.RUNNING, null, "feature/MES-123", tempDir.toString());
        verify(client, never()).updateStatus(eq(sessionId), eq(ConnectorSessionStatus.FAILED), any(), any(), any());
        assertThat(Files.readString(tempDir.resolve("task.md"))).isEqualTo("# task brief");
    }

    @Test
    void process_missingRepository_marksFailed() {
        ClaimedSessionResponse claimed = claimedSession();
        SessionContextResponse context = contextFor("MES-123", null);
        when(client.fetchContext(sessionId)).thenReturn(context);

        processor.process(claimed);

        verify(client).updateStatus(eq(sessionId), eq(ConnectorSessionStatus.FAILED), contains("No connected repository"), isNull(), isNull());
        verify(gitWorkspacePreparer, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void process_gitFailure_marksFailedWithErrorMessage() {
        ClaimedSessionResponse claimed = claimedSession();
        SessionContextResponse context = contextFor("MES-123", new RepositorySummary("org/repo",
                "https://github.com/org/repo", "https://github.com/org/repo.git", "main"));
        when(client.fetchContext(sessionId)).thenReturn(context);
        when(workspaceManager.prepare("MES-123")).thenReturn(tempDir);
        doThrow(new RuntimeException("clone failed")).when(gitWorkspacePreparer)
                .prepare(any(), any(), any(), any());

        processor.process(claimed);

        verify(client).updateStatus(sessionId, ConnectorSessionStatus.FAILED, "clone failed", null, null);
        verify(client, never()).updateStatus(eq(sessionId), eq(ConnectorSessionStatus.RUNNING), any(), any(), any());
    }

    @Test
    void process_fetchContextFails_stillReportsFailureWithoutThrowing() {
        ClaimedSessionResponse claimed = claimedSession();
        when(client.fetchContext(sessionId)).thenThrow(new SessionPollingException("backend unreachable"));

        processor.process(claimed);

        verify(client).updateStatus(sessionId, ConnectorSessionStatus.FAILED, "backend unreachable", null, null);
    }

    @Test
    void process_usesSessionIdWhenIdentifierMissing() {
        ClaimedSessionResponse claimed = claimedSession();
        SessionContextResponse context = new SessionContextResponse(
                sessionId, UUID.randomUUID(), null, "Title", null, "Open", "High",
                null, null, null, new RepositorySummary("org/repo", "https://github.com/org/repo",
                "https://github.com/org/repo.git", "main"));
        when(client.fetchContext(sessionId)).thenReturn(context);
        when(workspaceManager.prepare(sessionId.toString())).thenReturn(tempDir);
        when(contextBuilder.build(context)).thenReturn("brief");

        processor.process(claimed);

        verify(workspaceManager).prepare(sessionId.toString());
        verify(gitWorkspacePreparer).prepare(eq(tempDir), anyString(), anyString(), eq("feature/" + sessionId));
    }

    private ClaimedSessionResponse claimedSession() {
        return new ClaimedSessionResponse(sessionId, UUID.randomUUID(), UUID.randomUUID(), "CLAIMED",
                null, null, null, null, Instant.now(), Instant.now(), null, null, Instant.now(), Instant.now());
    }

    private SessionContextResponse contextFor(String identifier, RepositorySummary repository) {
        return new SessionContextResponse(sessionId, UUID.randomUUID(), identifier, "Fix login", null,
                "Open", "High", null, null, null, repository);
    }
}
