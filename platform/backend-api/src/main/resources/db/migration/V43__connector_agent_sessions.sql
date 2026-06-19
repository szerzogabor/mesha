-- Queue of work items waiting to be claimed and executed by a registered connector agent.

CREATE TABLE connector_agent_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    agent_id        UUID REFERENCES connector_agents(id) ON DELETE SET NULL,
    issue_id        UUID REFERENCES issues(id) ON DELETE CASCADE,
    status          VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    instructions    TEXT,
    error_message   TEXT,
    queued_at       TIMESTAMP,
    claimed_at      TIMESTAMP,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_connector_agent_sessions_user_id ON connector_agent_sessions(user_id);
CREATE INDEX idx_connector_agent_sessions_agent_id ON connector_agent_sessions(agent_id);
CREATE INDEX idx_connector_agent_sessions_status ON connector_agent_sessions(status);
