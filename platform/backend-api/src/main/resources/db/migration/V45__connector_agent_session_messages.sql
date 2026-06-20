-- Follow-up chat messages exchanged between a user and a connector-backed agent session.
-- A USER message is queued here until the connector polls and marks it delivered.

CREATE TABLE connector_agent_session_messages (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID NOT NULL REFERENCES connector_agent_sessions(id) ON DELETE CASCADE,
    role          VARCHAR(20) NOT NULL,
    content       TEXT NOT NULL,
    delivered_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_connector_agent_session_messages_session_id ON connector_agent_session_messages(session_id);
