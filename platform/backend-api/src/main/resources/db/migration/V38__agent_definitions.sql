-- Idempotent recovery of V33 (schema already applied on production via original V33).
CREATE TABLE IF NOT EXISTS agent_definitions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id          UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name                  VARCHAR(100) NOT NULL,
    title                 VARCHAR(150) NOT NULL,
    description           TEXT,
    provider_type         VARCHAR(50)  NOT NULL,
    system_prompt         TEXT         NOT NULL,
    provider_parameters   JSONB        NOT NULL DEFAULT '{}',
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, name)
);

CREATE INDEX IF NOT EXISTS idx_agent_definitions_workspace_id ON agent_definitions(workspace_id);
CREATE INDEX IF NOT EXISTS idx_agent_definitions_active ON agent_definitions(workspace_id, active);

CREATE TABLE IF NOT EXISTS issue_agents (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id              UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    agent_definition_id   UUID NOT NULL REFERENCES agent_definitions(id) ON DELETE CASCADE,
    assigned_at           TIMESTAMP NOT NULL DEFAULT now(),
    assigned_by           UUID REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE (issue_id, agent_definition_id)
);

CREATE INDEX IF NOT EXISTS idx_issue_agents_issue_id ON issue_agents(issue_id);
CREATE INDEX IF NOT EXISTS idx_issue_agents_agent_definition_id ON issue_agents(agent_definition_id);
