-- Idempotent recovery of V32 (schema already applied on production via original V32).
-- Deduplicate github_pull_requests, keeping the most recently updated row per (repository_id, github_pr_number) pair.
DELETE FROM github_pull_requests
WHERE id NOT IN (
    SELECT DISTINCT ON (repository_id, github_pr_number) id
    FROM github_pull_requests
    ORDER BY repository_id, github_pr_number, updated_at DESC
);

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_github_pull_requests_repo_pr_number'
          AND conrelid = 'github_pull_requests'::regclass
    ) THEN
        ALTER TABLE github_pull_requests
            ADD CONSTRAINT uq_github_pull_requests_repo_pr_number
            UNIQUE (repository_id, github_pr_number);
    END IF;
END $$;
