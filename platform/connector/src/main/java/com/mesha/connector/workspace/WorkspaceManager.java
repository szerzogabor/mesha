package com.mesha.connector.workspace;

import com.mesha.connector.config.WorkspaceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Owns the isolated, per-ticket workspace directories a connector clones repositories and writes
 * task briefs into, e.g. {@code ~/mesha-workspaces/MES-123}. Folders are keyed by issue
 * identifier rather than session id so retried or follow-up sessions for the same ticket reuse
 * the existing clone instead of re-cloning from scratch.
 */
@Component
public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final Path root;
    private final CleanupPolicy cleanupPolicy;

    public WorkspaceManager(WorkspaceProperties properties) {
        this.root = Path.of(properties.root());
        this.cleanupPolicy = parsePolicy(properties.cleanupPolicy());
    }

    private static CleanupPolicy parsePolicy(String value) {
        if (value == null || value.isBlank()) {
            return CleanupPolicy.NEVER;
        }
        try {
            return CleanupPolicy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new WorkspaceException("Invalid connector.workspace.cleanup-policy: " + value);
        }
    }

    /** Returns the workspace directory for {@code issueIdentifier}, creating it if absent. */
    public Path prepare(String issueIdentifier) {
        if (issueIdentifier == null || issueIdentifier.isBlank()) {
            throw new WorkspaceException("Cannot prepare a workspace without an issue identifier");
        }
        Path workspaceDir = root.resolve(sanitize(issueIdentifier));
        try {
            boolean existed = Files.exists(workspaceDir);
            Files.createDirectories(workspaceDir);
            log.info("workspace_prepared identifier={} path={} reused={}", issueIdentifier, workspaceDir, existed);
            return workspaceDir;
        } catch (IOException e) {
            throw new WorkspaceException("Failed to create workspace directory " + workspaceDir, e);
        }
    }

    /** Applies the configured cleanup policy now that the session for {@code issueIdentifier} has finished. */
    public void cleanup(String issueIdentifier, boolean succeeded) {
        boolean shouldDelete = cleanupPolicy == CleanupPolicy.ALWAYS
                || (cleanupPolicy == CleanupPolicy.ON_SUCCESS && succeeded);
        if (!shouldDelete) {
            return;
        }
        Path workspaceDir = root.resolve(sanitize(issueIdentifier));
        if (!Files.exists(workspaceDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(workspaceDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("workspace_cleanup_failed path={} error={}", path, e.getMessage());
                }
            });
            log.info("workspace_cleaned_up identifier={} path={}", issueIdentifier, workspaceDir);
        } catch (IOException e) {
            log.warn("workspace_cleanup_failed identifier={} path={} error={}", issueIdentifier, workspaceDir, e.getMessage());
        }
    }

    /** Issue identifiers are project-key-and-number (e.g. {@code MES-123}); strip anything unsafe for a path segment. */
    private static String sanitize(String issueIdentifier) {
        return issueIdentifier.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
