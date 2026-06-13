-- Allow null condition_value for condition types that require no value
-- (ASSIGNED_TO_AGENT and ASSIGNED_TO_HUMAN have no associated value)
ALTER TABLE ticket_rule_conditions ALTER COLUMN condition_value DROP NOT NULL;
