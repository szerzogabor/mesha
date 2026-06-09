ALTER TABLE github_pull_requests ADD COLUMN blocks_session_id UUID;

ALTER TABLE github_pull_requests
    ADD CONSTRAINT fk_github_pull_requests_blocks_session
        FOREIGN KEY (blocks_session_id) REFERENCES blocks_sessions(id) ON DELETE SET NULL;

CREATE INDEX idx_github_pull_requests_blocks_session_id ON github_pull_requests(blocks_session_id);
