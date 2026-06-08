-- Add session_url to persist the Blocks dashboard link for each session.
-- Nullable: populated when providerSessionId is known (either on dispatch or via webhook).
ALTER TABLE blocks_sessions ADD COLUMN session_url TEXT;
