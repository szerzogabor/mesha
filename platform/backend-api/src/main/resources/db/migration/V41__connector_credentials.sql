-- Stores opaque connector access/refresh token pairs issued to the Mesha Connector CLI.
-- Tokens themselves are never persisted; only their SHA-256 hashes are stored.

CREATE TABLE connector_credentials (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token_hash         VARCHAR(64) NOT NULL,
    access_token_expires_at   TIMESTAMP NOT NULL,
    refresh_token_hash        VARCHAR(64) NOT NULL,
    refresh_token_expires_at  TIMESTAMP NOT NULL,
    created_at                TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_connector_credentials_access_token_hash ON connector_credentials(access_token_hash);
CREATE UNIQUE INDEX idx_connector_credentials_refresh_token_hash ON connector_credentials(refresh_token_hash);
CREATE INDEX idx_connector_credentials_user_id ON connector_credentials(user_id);
