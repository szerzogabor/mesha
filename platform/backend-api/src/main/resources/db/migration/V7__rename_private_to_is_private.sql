-- Rename column to match Hibernate naming convention for the isPrivate field
ALTER TABLE github_repositories RENAME COLUMN private TO is_private;
