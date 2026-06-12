-- Allow action_value to be null for parameterless actions (e.g. START_AI_SESSION)
ALTER TABLE automation_rule_actions ALTER COLUMN action_value DROP NOT NULL;
