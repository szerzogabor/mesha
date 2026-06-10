-- Dynamic per-project statuses to replace hardcoded IssueStatus enum

CREATE TABLE project_statuses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(50) NOT NULL,
    color       VARCHAR(7)  NOT NULL DEFAULT '#6366f1',
    position    INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

CREATE INDEX idx_project_statuses_project_id ON project_statuses(project_id);

-- Seed default statuses for all existing projects
INSERT INTO project_statuses (project_id, name, color, position)
SELECT p.id, s.name, s.color, s.position
FROM projects p
CROSS JOIN (VALUES
    ('BACKLOG',     '#94a3b8', 0),
    ('TODO',        '#3b82f6', 1),
    ('IN_PROGRESS', '#f59e0b', 2),
    ('REVIEW',      '#8b5cf6', 3),
    ('DONE',        '#22c55e', 4)
) AS s(name, color, position);
