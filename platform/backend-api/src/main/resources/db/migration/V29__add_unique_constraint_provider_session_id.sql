DELETE FROM blocks_sessions a
USING blocks_sessions b
WHERE a.id > b.id
  AND a.provider_session_id = b.provider_session_id
  AND a.provider_session_id IS NOT NULL;

ALTER TABLE blocks_sessions
    ADD CONSTRAINT uq_blocks_sessions_provider_session_id UNIQUE (provider_session_id);
