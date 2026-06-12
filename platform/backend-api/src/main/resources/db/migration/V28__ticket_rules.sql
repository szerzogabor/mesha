-- Ticket rules: enforce conditions + restrictions on issue actions (start AI session, move to status)

CREATE TABLE ticket_rules (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by  UUID        REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_rules_project_id ON ticket_rules(project_id);

-- Conditions evaluated against the issue being acted upon (all conditions must match = AND logic)
CREATE TABLE ticket_rule_conditions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id          UUID        NOT NULL REFERENCES ticket_rules(id) ON DELETE CASCADE,
    condition_type   VARCHAR(50) NOT NULL,  -- HAS_STATUS | HAS_LABEL
    condition_value  VARCHAR(255) NOT NULL, -- status name or label UUID
    position         INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_ticket_rule_conditions_rule_id ON ticket_rule_conditions(rule_id);

-- Restrictions applied when all conditions match
CREATE TABLE ticket_rule_restrictions (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id           UUID        NOT NULL REFERENCES ticket_rules(id) ON DELETE CASCADE,
    restriction_type  VARCHAR(50) NOT NULL,   -- CANNOT_START_AI_SESSION | CANNOT_MOVE_TO_STATUS
    restriction_value VARCHAR(255),           -- null for CANNOT_START_AI_SESSION; status name for CANNOT_MOVE_TO_STATUS
    position          INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_ticket_rule_restrictions_rule_id ON ticket_rule_restrictions(rule_id);
