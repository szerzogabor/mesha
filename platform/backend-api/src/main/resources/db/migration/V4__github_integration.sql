-- GitHub App integration: installations, repositories, webhook events, pull requests

CREATE TABLE github_installations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    installation_id BIGINT NOT NULL UNIQUE,
    app_id          BIGINT NOT NULL,
    account_login   TEXT NOT NULL,
    account_type    VARCHAR(20) NOT NULL DEFAULT 'User',
    account_avatar_url TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE github_repositories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id UUID NOT NULL REFERENCES github_installations(id) ON DELETE CASCADE,
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    github_repo_id  BIGINT NOT NULL,
    owner           TEXT NOT NULL,
    name            TEXT NOT NULL,
    full_name       TEXT NOT NULL UNIQUE,
    private         BOOLEAN NOT NULL DEFAULT false,
    default_branch  TEXT NOT NULL DEFAULT 'main',
    description     TEXT,
    html_url        TEXT NOT NULL,
    connected       BOOLEAN NOT NULL DEFAULT true,
    last_synced_at  TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE github_webhook_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id TEXT NOT NULL UNIQUE,
    event_type  VARCHAR(50) NOT NULL,
    payload     TEXT NOT NULL,
    processed   BOOLEAN NOT NULL DEFAULT false,
    error       TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE github_pull_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id   UUID NOT NULL REFERENCES github_repositories(id) ON DELETE CASCADE,
    github_pr_number INTEGER NOT NULL,
    title           TEXT NOT NULL,
    body            TEXT,
    state           VARCHAR(20) NOT NULL DEFAULT 'open',
    author_login    TEXT,
    author_avatar_url TEXT,
    source_branch   TEXT NOT NULL,
    target_branch   TEXT NOT NULL,
    html_url        TEXT NOT NULL,
    draft           BOOLEAN NOT NULL DEFAULT false,
    commits_count   INTEGER NOT NULL DEFAULT 0,
    review_state    VARCHAR(30),
    checks_status   VARCHAR(30),
    merged_at       TIMESTAMP,
    closed_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (repository_id, github_pr_number)
);

CREATE INDEX idx_github_installations_workspace_id ON github_installations(workspace_id);
CREATE INDEX idx_github_installations_installation_id ON github_installations(installation_id);
CREATE INDEX idx_github_repositories_workspace_id ON github_repositories(workspace_id);
CREATE INDEX idx_github_repositories_installation_id ON github_repositories(installation_id);
CREATE INDEX idx_github_repositories_full_name ON github_repositories(full_name);
CREATE INDEX idx_github_webhook_events_delivery_id ON github_webhook_events(delivery_id);
CREATE INDEX idx_github_webhook_events_event_type ON github_webhook_events(event_type);
CREATE INDEX idx_github_pull_requests_repository_id ON github_pull_requests(repository_id);
CREATE INDEX idx_github_pull_requests_state ON github_pull_requests(state);
