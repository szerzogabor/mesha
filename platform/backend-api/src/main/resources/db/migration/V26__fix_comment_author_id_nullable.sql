-- The author_id column is legacy (from V1) and is no longer used by the JPA entity.
-- The entity uses user_id (added in V3) instead. Drop the NOT NULL constraint so
-- inserts succeed without setting this unused column.
ALTER TABLE comments ALTER COLUMN author_id DROP NOT NULL;
