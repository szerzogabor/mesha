-- Project key (short uppercase prefix, e.g. "MESH")
ALTER TABLE projects ADD COLUMN key VARCHAR(10);

-- Issue number (sequential per project)
ALTER TABLE issues ADD COLUMN number INTEGER;

-- Backfill existing issues with sequential numbers per project
UPDATE issues i SET number = sub.rn
FROM (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY created_at) AS rn
  FROM issues
) sub
WHERE i.id = sub.id;

ALTER TABLE issues ALTER COLUMN number SET NOT NULL;
CREATE UNIQUE INDEX uq_issues_project_number ON issues(project_id, number);
