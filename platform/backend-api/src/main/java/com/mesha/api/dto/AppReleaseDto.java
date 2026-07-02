package com.mesha.api.dto;

import com.mesha.api.model.AppRelease;

import java.time.Instant;
import java.util.UUID;

/**
 * Public metadata for a client release. Deliberately excludes the APK bytes — the
 * binary is hosted externally (e.g. a GitHub Release asset) and referenced by
 * {@link #downloadUrl}, so this payload stays cheap to fetch for the marketing site
 * and the in-app update check. {@code downloadUrl} is empty (never null) for legacy
 * releases uploaded before V50, which only stored the APK bytes inline.
 */
public record AppReleaseDto(
        UUID id,
        String platform,
        String versionName,
        int versionCode,
        String releaseNotes,
        int minSdk,
        String fileName,
        long fileSize,
        String checksumSha256,
        boolean published,
        String downloadUrl,
        Instant createdAt
) {
    public static AppReleaseDto from(AppRelease r) {
        return new AppReleaseDto(
                r.getId(),
                r.getPlatform().name(),
                r.getVersionName(),
                r.getVersionCode(),
                r.getReleaseNotes(),
                r.getMinSdk(),
                r.getFileName(),
                r.getFileSize(),
                r.getChecksumSha256(),
                r.isPublished(),
                r.getDownloadUrl() != null ? r.getDownloadUrl() : "",
                r.getCreatedAt()
        );
    }
}
