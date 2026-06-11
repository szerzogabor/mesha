CREATE TABLE issue_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    target_issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    link_type VARCHAR(20) NOT NULL,
    created_by_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_issue_link UNIQUE (source_issue_id, target_issue_id, link_type),
    CONSTRAINT chk_no_self_link CHECK (source_issue_id <> target_issue_id)
);

CREATE INDEX idx_issue_links_source_id ON issue_links(source_issue_id);
CREATE INDEX idx_issue_links_target_id ON issue_links(target_issue_id);
