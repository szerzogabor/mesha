-- Releases now host their APK bytes externally (e.g. a GitHub Release asset) instead of
-- inline as bytea: the backend runs on a memory-constrained instance that cannot buffer
-- a ~100MB multipart upload/download without OOMing. New releases populate download_url
-- and leave content null; content stays nullable so old rows aren't touched.
ALTER TABLE app_releases ADD COLUMN download_url VARCHAR(2048);
ALTER TABLE app_releases ALTER COLUMN content DROP NOT NULL;
