-- One automation rule can now fire multiple actions: move actions to a child table

CREATE TABLE automation_rule_actions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id      UUID NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    action_type  VARCHAR(50) NOT NULL,
    action_value VARCHAR(255) NOT NULL,
    position     INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_automation_rule_actions_rule_id ON automation_rule_actions(rule_id);

-- Migrate existing single-action rules
INSERT INTO automation_rule_actions (rule_id, action_type, action_value, position)
SELECT id, action_type, action_value, 0 FROM automation_rules;

ALTER TABLE automation_rules DROP COLUMN action_type;
ALTER TABLE automation_rules DROP COLUMN action_value;
