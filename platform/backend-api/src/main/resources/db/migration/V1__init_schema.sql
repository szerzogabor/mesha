-- Initial schema migration

CREATE TABLE workspaces (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    slug       TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    description  TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE issues (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title               TEXT NOT NULL,
    description         TEXT,
    status              VARCHAR(30) NOT NULL DEFAULT 'open',
    priority            VARCHAR(20) NOT NULL DEFAULT 'medium',
    ai_assignment_state VARCHAR(30),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id   UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    body       TEXT NOT NULL,
    author_id  TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ai_sessions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id             UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    provider             VARCHAR(50) NOT NULL,
    provider_session_id  TEXT NOT NULL,
    status               VARCHAR(30) NOT NULL DEFAULT 'pending',
    retry_count          INTEGER NOT NULL DEFAULT 0,
    context_version      INTEGER NOT NULL DEFAULT 1,
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE pull_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id         UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    github_pr_number INTEGER,
    github_repo      TEXT,
    title            TEXT,
    state            VARCHAR(20),
    url              TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE workflow_runs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id   UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    type       VARCHAR(50) NOT NULL,
    status     VARCHAR(30) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_issues_project_id ON issues(project_id);
CREATE INDEX idx_ai_sessions_issue_id ON ai_sessions(issue_id);
CREATE INDEX idx_pull_requests_issue_id ON pull_requests(issue_id);
CREATE INDEX idx_workflow_runs_issue_id ON workflow_runs(issue_id);
