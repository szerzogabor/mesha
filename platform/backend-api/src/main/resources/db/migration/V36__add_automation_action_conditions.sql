CREATE TABLE automation_rule_action_conditions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_id UUID NOT NULL REFERENCES automation_rule_actions(id) ON DELETE CASCADE,
    condition_type VARCHAR(50) NOT NULL,
    condition_value VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_automation_rule_action_conditions_action_id ON automation_rule_action_conditions(action_id);
