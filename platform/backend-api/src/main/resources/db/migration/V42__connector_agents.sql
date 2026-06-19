-- Registered Mesha Connector instances ("agents"). Each row represents one local
-- connector install (identified by hostname + executor type) owned by a user.

CREATE TABLE connector_agents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    hostname            VARCHAR(255) NOT NULL,
    executor_type       VARCHAR(100) NOT NULL,
    connector_version   VARCHAR(50) NOT NULL,
    capabilities        JSONB NOT NULL DEFAULT '[]',
    registered_at       TIMESTAMP NOT NULL DEFAULT now(),
    last_seen_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (user_id, hostname, executor_type)
);

CREATE INDEX idx_connector_agents_user_id ON connector_agents(user_id);
