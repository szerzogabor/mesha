package com.mesha.connector.workspace;

import com.mesha.connector.config.WorkspaceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceManagerTest {

    @TempDir
    Path root;

    @Test
    void prepare_createsWorkspaceDirectory() {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "NEVER"));

        Path workspace = manager.prepare("MES-123");

        assertThat(workspace).isDirectory();
        assertThat(workspace).isEqualTo(root.resolve("MES-123"));
    }

    @Test
    void prepare_reusesExistingWorkspace() {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "NEVER"));

        Path first = manager.prepare("MES-123");
        Path second = manager.prepare("MES-123");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void prepare_isolatesDifferentIssues() {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "NEVER"));

        Path first = manager.prepare("MES-123");
        Path second = manager.prepare("MES-456");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isDirectory();
        assertThat(second).isDirectory();
    }

    @Test
    void prepare_sanitizesUnsafeCharacters() {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "NEVER"));

        Path workspace = manager.prepare("MES/123:../etc");

        assertThat(workspace.getParent()).isEqualTo(root);
        assertThat(workspace).isDirectory();
    }

    @Test
    void prepare_blankIdentifier_throws() {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "NEVER"));

        assertThatThrownBy(() -> manager.prepare(" ")).isInstanceOf(WorkspaceException.class);
    }

    @Test
    void constructor_invalidCleanupPolicy_throws() {
        assertThatThrownBy(() -> new WorkspaceManager(new WorkspaceProperties(root.toString(), "SOMETIMES")))
                .isInstanceOf(WorkspaceException.class);
    }

    @Test
    void cleanup_alwaysPolicy_deletesWorkspaceRegardlessOfOutcome() throws IOException {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "ALWAYS"));
        Path workspace = manager.prepare("MES-123");
        Files.writeString(workspace.resolve("task.md"), "content");

        manager.cleanup("MES-123", false);

        assertThat(workspace).doesNotExist();
    }

    @Test
    void cleanup_onSuccessPolicy_keepsWorkspaceAfterFailure() {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "ON_SUCCESS"));
        Path workspace = manager.prepare("MES-123");

        manager.cleanup("MES-123", false);

        assertThat(workspace).isDirectory();
    }

    @Test
    void cleanup_onSuccessPolicy_deletesWorkspaceAfterSuccess() {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "ON_SUCCESS"));
        Path workspace = manager.prepare("MES-123");

        manager.cleanup("MES-123", true);

        assertThat(workspace).doesNotExist();
    }

    @Test
    void cleanup_neverPolicy_keepsWorkspace() {
        WorkspaceManager manager = new WorkspaceManager(new WorkspaceProperties(root.toString(), "NEVER"));
        Path workspace = manager.prepare("MES-123");

        manager.cleanup("MES-123", true);

        assertThat(workspace).isDirectory();
    }
}
