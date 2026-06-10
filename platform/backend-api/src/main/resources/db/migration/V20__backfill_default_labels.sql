-- Backfill default labels (Bug, Feature, Improvement) for workspaces that don't have them yet

INSERT INTO labels (workspace_id, name, color)
SELECT w.id, 'Bug', '#EB5757'
FROM workspaces w
WHERE NOT EXISTS (
    SELECT 1 FROM labels l WHERE l.workspace_id = w.id AND l.name = 'Bug'
);

INSERT INTO labels (workspace_id, name, color)
SELECT w.id, 'Feature', '#BB87FC'
FROM workspaces w
WHERE NOT EXISTS (
    SELECT 1 FROM labels l WHERE l.workspace_id = w.id AND l.name = 'Feature'
);

INSERT INTO labels (workspace_id, name, color)
SELECT w.id, 'Improvement', '#4EA7FC'
FROM workspaces w
WHERE NOT EXISTS (
    SELECT 1 FROM labels l WHERE l.workspace_id = w.id AND l.name = 'Improvement'
);
