-- Per-user OpenAI / ChatGPT credential for AI ticket-draft generation.
-- Supports two auth modes:
--   API_KEY       -> a standard sk-... key stored in api_key_enc (billed per-usage)
--   CHATGPT_TOKEN -> a ChatGPT-subscription OAuth credential pulled from Codex
--                    desktop's ~/.codex/auth.json (access/refresh tokens + account id)
-- All secret material is stored AES-encrypted (see SecretCipher).

CREATE TABLE user_openai_config (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID         NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    auth_mode                VARCHAR(20)  NOT NULL,
    api_key_enc              TEXT,
    access_token_enc         TEXT,
    refresh_token_enc        TEXT,
    account_id               VARCHAR(128),
    access_token_expires_at  TIMESTAMP,
    model                    VARCHAR(64),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'connected',
    connected_at             TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_openai_config_user_id ON user_openai_config(user_id);
