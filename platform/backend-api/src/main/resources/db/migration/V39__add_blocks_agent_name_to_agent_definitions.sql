-- Idempotent recovery of V34 (schema already applied on production via original V34).
ALTER TABLE agent_definitions
    ADD COLUMN IF NOT EXISTS blocks_agent_name VARCHAR(100);
