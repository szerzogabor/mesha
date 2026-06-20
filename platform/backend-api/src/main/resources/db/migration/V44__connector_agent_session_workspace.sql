-- Tracks the git branch and local workspace path a connector prepared while executing a session.

ALTER TABLE connector_agent_sessions
    ADD COLUMN branch_name VARCHAR(255),
    ADD COLUMN workspace_path TEXT;
