-- Blocks AI orchestration session tracking

CREATE TABLE blocks_sessions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id             UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    provider             VARCHAR(50) NOT NULL DEFAULT 'blocks',
    provider_session_id  TEXT,
    execution_state      VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    retry_count          INTEGER NOT NULL DEFAULT 0,
    pr_url               TEXT,
    pr_number            INTEGER,
    branch_name          TEXT,
    error_message        TEXT,
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_blocks_sessions_issue_id ON blocks_sessions(issue_id);
CREATE INDEX idx_blocks_sessions_execution_state ON blocks_sessions(execution_state);

-- Extend issues table with blocks-specific AI state
ALTER TABLE issues ADD COLUMN IF NOT EXISTS blocks_session_id UUID REFERENCES blocks_sessions(id);
