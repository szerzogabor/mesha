package com.mesha.api.dto;

import com.mesha.api.model.AppRelease;

import java.time.Instant;
import java.util.UUID;

/**
 * Public metadata for a client release. Deliberately excludes the APK bytes —
 * the binary is served separately via the download endpoint so this payload stays
 * cheap to fetch for the marketing site and the in-app update check.
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
                "/api/releases/" + r.getId() + "/download",
                r.getCreatedAt()
        );
    }
}
