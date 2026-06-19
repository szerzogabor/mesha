package com.mesha.connector.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises {@link GitWorkspacePreparer} against the real {@code git} binary using local temp repos. */
class GitWorkspacePreparerTest {

    @TempDir
    Path tempDir;

    private final GitWorkspacePreparer preparer = new GitWorkspacePreparer();
    private Path remote;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        remote = tempDir.resolve("remote.git");
        Files.createDirectories(remote);
        run(remote, "git", "init", "--bare", "--initial-branch=main");

        Path seed = tempDir.resolve("seed");
        Files.createDirectories(seed);
        run(seed, "git", "init", "--initial-branch=main");
        run(seed, "git", "config", "user.email", "test@example.com");
        run(seed, "git", "config", "user.name", "Test");
        Files.writeString(seed.resolve("README.md"), "hello");
        run(seed, "git", "add", "README.md");
        run(seed, "git", "commit", "-m", "initial commit");
        run(seed, "git", "remote", "add", "origin", remote.toString());
        run(seed, "git", "push", "origin", "main");
    }

    @Test
    void prepare_clonesAndCreatesWorkingBranch() throws IOException {
        Path workspace = tempDir.resolve("workspace-1");
        Files.createDirectories(workspace);

        preparer.prepare(workspace, remote.toString(), "main", "feature/MES-123");

        assertThat(workspace.resolve(".git")).isDirectory();
        assertThat(workspace.resolve("README.md")).exists();
        assertThat(currentBranch(workspace)).isEqualTo("feature/MES-123");
    }

    @Test
    void prepare_reusesExistingCloneAndChecksOutExistingBranch() throws IOException {
        Path workspace = tempDir.resolve("workspace-2");
        Files.createDirectories(workspace);
        preparer.prepare(workspace, remote.toString(), "main", "feature/MES-123");

        preparer.prepare(workspace, remote.toString(), "main", "feature/MES-123");

        assertThat(currentBranch(workspace)).isEqualTo("feature/MES-123");
    }

    @Test
    void prepare_missingCloneUrl_throws() {
        Path workspace = tempDir.resolve("workspace-3");

        assertThatThrownBy(() -> preparer.prepare(workspace, "", "main", "feature/MES-123"))
                .isInstanceOf(GitCommandException.class);
    }

    private String currentBranch(Path workspace) {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                    .directory(workspace.toFile())
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();
            return output;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void run(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command));
        }
    }
}
