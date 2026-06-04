-- GitHub installation lifecycle: last_refresh_at column and audit log table

ALTER TABLE github_installations ADD COLUMN last_refresh_at TIMESTAMP;

CREATE TABLE github_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id UUID REFERENCES github_installations(id) ON DELETE SET NULL,
    event_type      VARCHAR(60) NOT NULL,
    details         TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_github_audit_log_installation_id ON github_audit_log(installation_id);
CREATE INDEX idx_github_audit_log_event_type ON github_audit_log(event_type);
CREATE INDEX idx_github_audit_log_created_at ON github_audit_log(created_at);
