package com.mesha.connector.session;

import com.mesha.connector.context.SessionContextBuilder;
import com.mesha.connector.git.GitWorkspacePreparer;
import com.mesha.connector.git.BranchNamingStrategy;
import com.mesha.connector.session.dto.ClaimedSessionResponse;
import com.mesha.connector.session.dto.SessionContextResponse;
import com.mesha.connector.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Prepares a claimed session for execution: marks it {@code PREPARING}, fetches its full ticket
 * context, prepares an isolated git workspace on a dedicated branch, writes the {@code task.md}
 * brief, then marks it {@code RUNNING}. Any failure along the way is reported back as
 * {@code FAILED} rather than left for the backend to time out.
 */
@Component
public class SessionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SessionProcessor.class);
    private static final String TASK_FILE_NAME = "task.md";

    private final ConnectorAgentSessionClient client;
    private final WorkspaceManager workspaceManager;
    private final GitWorkspacePreparer gitWorkspacePreparer;
    private final SessionContextBuilder contextBuilder;

    public SessionProcessor(ConnectorAgentSessionClient client, WorkspaceManager workspaceManager,
                             GitWorkspacePreparer gitWorkspacePreparer, SessionContextBuilder contextBuilder) {
        this.client = client;
        this.workspaceManager = workspaceManager;
        this.gitWorkspacePreparer = gitWorkspacePreparer;
        this.contextBuilder = contextBuilder;
    }

    public void process(ClaimedSessionResponse claimed) {
        UUID sessionId = claimed.id();
        String identifier = null;
        try {
            client.updateStatus(sessionId, ConnectorSessionStatus.PREPARING, null, null, null);

            SessionContextResponse context = client.fetchContext(sessionId);
            identifier = context.issueIdentifier() != null ? context.issueIdentifier() : sessionId.toString();

            SessionContextResponse.RepositorySummary repository = context.repository();
            if (repository == null) {
                throw new SessionPollingException("No connected repository found for ticket " + identifier);
            }

            Path workspaceDir = workspaceManager.prepare(identifier);
            String branchName = BranchNamingStrategy.branchFor(identifier);
            gitWorkspacePreparer.prepare(workspaceDir, repository.cloneUrl(), repository.defaultBranch(), branchName);

            writeTaskFile(workspaceDir, contextBuilder.build(context));

            client.updateStatus(sessionId, ConnectorSessionStatus.RUNNING, null, branchName, workspaceDir.toString());
            log.info("session_prepared sessionId={} identifier={} branch={}", sessionId, identifier, branchName);
        } catch (Exception e) {
            log.error("session_preparation_failed sessionId={} error={}", sessionId, e.getMessage(), e);
            safelyFail(sessionId, e);
            // The workspace, if one was created, won't be used by a session that failed to
            // prepare — apply the cleanup policy now rather than leaving it for a run that never starts.
            if (identifier != null) {
                workspaceManager.cleanup(identifier, false);
            }
        }
    }

    private void writeTaskFile(Path workspaceDir, String taskMarkdown) {
        try {
            Files.writeString(workspaceDir.resolve(TASK_FILE_NAME), taskMarkdown);
        } catch (IOException e) {
            throw new SessionPollingException("Failed to write " + TASK_FILE_NAME + " into " + workspaceDir, e);
        }
    }

    private void safelyFail(UUID sessionId, Exception cause) {
        try {
            client.updateStatus(sessionId, ConnectorSessionStatus.FAILED, cause.getMessage(), null, null);
        } catch (Exception e) {
            log.error("session_failure_report_failed sessionId={} error={}", sessionId, e.getMessage(), e);
        }
    }
}
