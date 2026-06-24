-- Stores downloadable native client builds (Android APKs today) distributed from the
-- marketing site and the in-app updater. The binary is stored as bytea to avoid an
-- external object-store dependency at the current scale, mirroring issue_attachments.
--
-- version_code is the monotonic integer the Android client compares against its own
-- BuildConfig.VERSION_CODE to decide whether an update is available; version_name is
-- the human-facing semantic version.

CREATE TABLE app_releases (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    platform        VARCHAR(31)  NOT NULL DEFAULT 'ANDROID',
    version_name    VARCHAR(63)  NOT NULL,
    version_code    INTEGER      NOT NULL,
    release_notes   TEXT,
    min_sdk         INTEGER      NOT NULL DEFAULT 33,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(127) NOT NULL DEFAULT 'application/vnd.android.package-archive',
    file_size       BIGINT       NOT NULL,
    content         BYTEA        NOT NULL,
    checksum_sha256 VARCHAR(64)  NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT TRUE,
    uploaded_by     UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_releases_platform_version_code UNIQUE (platform, version_code)
);

CREATE INDEX idx_app_releases_platform_published ON app_releases(platform, published);
CREATE INDEX idx_app_releases_version_code ON app_releases(platform, version_code DESC);
