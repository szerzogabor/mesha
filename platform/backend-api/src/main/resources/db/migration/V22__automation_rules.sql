-- Automation rules: predefined triggers (PR/Blocks events) firing actions on issues

CREATE TABLE automation_rules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    trigger_type VARCHAR(50) NOT NULL,
    action_type  VARCHAR(50) NOT NULL,
    action_value VARCHAR(255) NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by   UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_automation_rules_project_trigger ON automation_rules(project_id, trigger_type);
