ALTER TABLE agent_definitions
    ADD COLUMN IF NOT EXISTS blocks_agent_name VARCHAR(100);
