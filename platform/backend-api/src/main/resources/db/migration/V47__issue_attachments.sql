-- Stores files (images, documents) attached to tickets so assignees (human or AI) have richer context.
-- File content is stored as bytea; for the current scale this avoids an external object-store dependency.

CREATE TABLE issue_attachments (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id     UUID         NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    uploaded_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    file_size    BIGINT       NOT NULL,
    content      BYTEA        NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_issue_attachments_issue_id ON issue_attachments(issue_id);
