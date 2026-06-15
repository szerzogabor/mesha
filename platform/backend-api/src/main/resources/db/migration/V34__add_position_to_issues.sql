ALTER TABLE issues ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

UPDATE issues i
SET position = sub.row_num - 1
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY project_id, status ORDER BY created_at ASC) AS row_num
    FROM issues
) sub
WHERE i.id = sub.id;

CREATE INDEX idx_issues_project_status_position ON issues (project_id, status, position);
