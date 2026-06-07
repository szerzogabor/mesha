-- Blocks agent activity messages for live session feed

CREATE TABLE blocks_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES blocks_sessions(id) ON DELETE CASCADE,
    message     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_blocks_messages_session_id ON blocks_messages(session_id);
