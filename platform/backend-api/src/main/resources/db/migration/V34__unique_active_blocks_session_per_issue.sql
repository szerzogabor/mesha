-- Enforce at most one active (non-terminal) blocks session per issue.
-- This is a safety net for concurrent requests that bypass the application-level guard.
CREATE UNIQUE INDEX idx_unique_active_blocks_session_per_issue
    ON blocks_sessions (issue_id)
    WHERE execution_state NOT IN ('DONE', 'FAILED', 'CANCELED');
