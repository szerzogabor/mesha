-- Tracks the pull request a connector opened for a session, reported by the connector itself
-- (no GitHub App webhook sync needed since the connector pushes the branch and opens the PR locally).

ALTER TABLE connector_agent_sessions
    ADD COLUMN pr_url TEXT,
    ADD COLUMN pr_number INTEGER,
    ADD COLUMN pr_title VARCHAR(500),
    ADD COLUMN pr_reported_at TIMESTAMP;
