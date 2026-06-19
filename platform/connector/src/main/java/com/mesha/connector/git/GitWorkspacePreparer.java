package com.mesha.connector.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Clones (or reuses) a repository into a session's workspace and checks out the working branch,
 * shelling out to the system {@code git} binary rather than depending on a JGit-style library.
 */
@Component
public class GitWorkspacePreparer {

    private static final Logger log = LoggerFactory.getLogger(GitWorkspacePreparer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    /**
     * Ensures {@code workspaceDir} contains an up-to-date clone of {@code cloneUrl} on
     * {@code branchName}, creating that branch from {@code defaultBranch} if it doesn't exist yet.
     */
    public void prepare(Path workspaceDir, String cloneUrl, String defaultBranch, String branchName) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw new GitCommandException("Cannot prepare a git workspace without a repository clone URL");
        }
        String branch = (defaultBranch == null || defaultBranch.isBlank()) ? "main" : defaultBranch;

        if (isGitRepo(workspaceDir)) {
            updateExistingClone(workspaceDir, branch);
        } else {
            clone(workspaceDir, cloneUrl);
        }
        checkoutWorkingBranch(workspaceDir, branchName);
        log.info("git_workspace_ready path={} branch={}", workspaceDir, branchName);
    }

    private boolean isGitRepo(Path workspaceDir) {
        return Files.isDirectory(workspaceDir.resolve(".git"));
    }

    private void clone(Path workspaceDir, String cloneUrl) {
        run(workspaceDir, "git", "clone", cloneUrl, ".");
    }

    private void updateExistingClone(Path workspaceDir, String defaultBranch) {
        run(workspaceDir, "git", "fetch", "origin");
        run(workspaceDir, "git", "checkout", defaultBranch);
        run(workspaceDir, "git", "pull", "origin", defaultBranch);
    }

    private void checkoutWorkingBranch(Path workspaceDir, String branchName) {
        GitCommandResult check = exec(workspaceDir, "git", "rev-parse", "--verify", branchName);
        if (check.exitCode() == 0) {
            run(workspaceDir, "git", "checkout", branchName);
        } else {
            run(workspaceDir, "git", "checkout", "-b", branchName);
        }
    }

    private void run(Path workspaceDir, String... command) {
        GitCommandResult result = exec(workspaceDir, command);
        if (result.exitCode() != 0) {
            throw new GitCommandException("git command failed (" + String.join(" ", command) + "): " + result.output().trim());
        }
    }

    private GitCommandResult exec(Path workspaceDir, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workspaceDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitCommandException("git command timed out: " + String.join(" ", command));
            }
            return new GitCommandResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new GitCommandException("Failed to run git command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("Interrupted while running git command: " + String.join(" ", command), e);
        }
    }

    private record GitCommandResult(int exitCode, String output) {}
}
