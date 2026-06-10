-- Step 1: Derive candidate key from project name
-- Take first letter of each word (after stripping punctuation), uppercase, max 5 chars
WITH candidates AS (
  SELECT
    id,
    workspace_id,
    created_at,
    UPPER(
      LEFT(
        ARRAY_TO_STRING(
          ARRAY(
            SELECT SUBSTRING(word FROM 1 FOR 1)
            FROM UNNEST(
              STRING_TO_ARRAY(
                TRIM(REGEXP_REPLACE(name, '[^A-Za-z0-9 ]', '', 'g')),
                ' '
              )
            ) AS word
            WHERE TRIM(word) != ''
            LIMIT 5
          ),
          ''
        ),
        5
      )
    ) AS candidate
  FROM projects
  WHERE key IS NULL
),
-- Step 2: Append row-number suffix to resolve duplicates within same workspace
deduped AS (
  SELECT
    id,
    CASE
      WHEN ROW_NUMBER() OVER (PARTITION BY workspace_id, candidate ORDER BY created_at) = 1
        THEN candidate
      ELSE candidate || CAST(
             ROW_NUMBER() OVER (PARTITION BY workspace_id, candidate ORDER BY created_at)
           AS VARCHAR)
    END AS final_key
  FROM candidates
  WHERE candidate IS NOT NULL AND candidate != ''
)
UPDATE projects p
SET key = d.final_key
FROM deduped d
WHERE p.id = d.id;

-- Fallback: projects whose name was entirely special characters / empty
UPDATE projects
SET key = 'P' || UPPER(LEFT(REPLACE(CAST(id AS VARCHAR), '-', ''), 4))
WHERE key IS NULL OR LENGTH(key) < 2;
