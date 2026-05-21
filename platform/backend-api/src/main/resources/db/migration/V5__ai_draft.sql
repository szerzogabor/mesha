CREATE TABLE ai_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    prompt TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    generated_title TEXT,
    generated_description TEXT,
    acceptance_criteria TEXT,
    suggested_labels TEXT,
    priority_suggestion VARCHAR(20),
    implementation_notes TEXT,
    scope_notes TEXT,
    out_of_scope_notes TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_drafts_project_id ON ai_drafts(project_id);
CREATE INDEX idx_ai_drafts_created_by ON ai_drafts(created_by);
