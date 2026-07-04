-- Push-notification device registry: maps a user to the FCM registration tokens of
-- their devices. A device registers its token after sign-in and removes it when the
-- user turns notifications off (or on sign-out). Status-change notifications fan out
-- to every token belonging to a member of the affected project's workspace.
CREATE TABLE user_devices (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fcm_token   TEXT NOT NULL,
    platform    VARCHAR(20) NOT NULL DEFAULT 'ANDROID',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_devices_token UNIQUE (fcm_token)
);

CREATE INDEX idx_user_devices_user_id ON user_devices (user_id);
