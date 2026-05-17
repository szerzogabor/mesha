-- Project management workflow: assignees, labels, activity events, threaded comments

ALTER TABLE issues
    ADD COLUMN IF NOT EXISTS assignee_id UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE issues
    ALTER COLUMN status SET DEFAULT 'backlog';

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS parent_comment_id UUID REFERENCES comments(id) ON DELETE CASCADE;

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE labels (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    color        VARCHAR(7) NOT NULL DEFAULT '#6366f1',
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, name)
);

CREATE TABLE issue_labels (
    issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    PRIMARY KEY (issue_id, label_id)
);

CREATE TABLE activity_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id   UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    old_value  TEXT,
    new_value  TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_issues_assignee_id       ON issues(assignee_id);
CREATE INDEX idx_comments_issue_id        ON comments(issue_id);
CREATE INDEX idx_comments_parent_id       ON comments(parent_comment_id);
CREATE INDEX idx_labels_workspace_id      ON labels(workspace_id);
CREATE INDEX idx_issue_labels_issue_id    ON issue_labels(issue_id);
CREATE INDEX idx_activity_events_issue_id ON activity_events(issue_id);
CREATE INDEX idx_projects_workspace_id    ON projects(workspace_id);
