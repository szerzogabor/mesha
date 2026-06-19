package com.mesha.connector.workspace;

/** Controls whether {@link WorkspaceManager} deletes a session's workspace once it finishes. */
public enum CleanupPolicy {
    /** Keep every workspace so a later session for the same ticket can reuse the clone. */
    NEVER,
    /** Delete the workspace once a session completes successfully; keep it after a failure for debugging. */
    ON_SUCCESS,
    /** Always delete the workspace once a session reaches a terminal state. */
    ALWAYS
}
