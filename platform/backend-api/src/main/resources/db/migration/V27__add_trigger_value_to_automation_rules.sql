ALTER TABLE automation_rules
    ADD COLUMN IF NOT EXISTS trigger_value VARCHAR(255);
