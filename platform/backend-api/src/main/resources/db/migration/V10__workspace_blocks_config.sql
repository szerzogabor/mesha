-- Per-workspace Blocks integration configuration (API key stored encrypted)

CREATE TABLE workspace_blocks_config (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID         NOT NULL UNIQUE REFERENCES workspaces(id) ON DELETE CASCADE,
    api_key_enc  TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'connected',
    connected_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_workspace_blocks_config_workspace_id ON workspace_blocks_config(workspace_id);
