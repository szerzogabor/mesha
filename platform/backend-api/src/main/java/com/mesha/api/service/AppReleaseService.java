package com.mesha.api.service;

import com.mesha.api.model.AppPlatform;
import com.mesha.api.model.AppRelease;
import com.mesha.api.model.User;
import com.mesha.api.repository.AppReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Manages the lifecycle of downloadable native client releases (Android APKs).
 *
 * <p>Reading (latest / list / download) is public so the marketing site and the
 * in-app updater can consume it without authentication. Uploading and deleting are
 * gated to platform admins by the controller's {@code @PreAuthorize} guard.
 */
@Service
public class AppReleaseService {

    private static final Logger log = LoggerFactory.getLogger(AppReleaseService.class);
    private static final String APK_CONTENT_TYPE = "application/vnd.android.package-archive";

    private final AppReleaseRepository releaseRepository;
    private final long maxApkSizeBytes;

    public AppReleaseService(AppReleaseRepository releaseRepository,
                             @Value("${app.releases.max-apk-size-bytes:209715200}") long maxApkSizeBytes) {
        this.releaseRepository = releaseRepository;
        this.maxApkSizeBytes = maxApkSizeBytes;
    }

    @Transactional(readOnly = true)
    public AppRelease getLatest(AppPlatform platform) {
        return releaseRepository.findFirstByPlatformAndPublishedTrueOrderByVersionCodeDesc(platform)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No published release available for " + platform));
    }

    @Transactional(readOnly = true)
    public List<AppRelease> listPublished(AppPlatform platform) {
        return releaseRepository.findAllByPlatformAndPublishedTrueOrderByVersionCodeDesc(platform);
    }

    @Transactional(readOnly = true)
    public List<AppRelease> listAll(AppPlatform platform) {
        return releaseRepository.findAllByPlatformOrderByVersionCodeDesc(platform);
    }

    @Transactional(readOnly = true)
    public AppRelease getById(UUID id) {
        return releaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found"));
    }

    @Transactional
    public AppRelease upload(AppPlatform platform,
                             String versionName,
                             int versionCode,
                             Integer minSdk,
                             String releaseNotes,
                             boolean published,
                             String fileName,
                             long fileSize,
                             String checksumSha256,
                             String downloadUrl,
                             User uploader) {
        if (!StringUtils.hasText(versionName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionName is required");
        }
        if (versionCode <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionCode must be a positive integer");
        }
        if (!StringUtils.hasText(downloadUrl) || !downloadUrl.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "downloadUrl must be an https URL");
        }
        if (downloadUrl.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "downloadUrl must be 2048 characters or less");
        }
        if (!StringUtils.hasText(checksumSha256)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checksumSha256 is required");
        }
        if (checksumSha256.length() != 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checksumSha256 must be exactly 64 characters");
        }
        if (fileSize <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileSize must be a positive integer");
        }
        if (fileSize > maxApkSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "APK exceeds maximum allowed size of " + (maxApkSizeBytes / (1024 * 1024)) + " MB");
        }
        if (releaseRepository.existsByPlatformAndVersionCode(platform, versionCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A release with versionCode " + versionCode + " already exists for " + platform);
        }
        if (fileName == null || !fileName.toLowerCase().endsWith(".apk")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "fileName must end with .apk");
        }
        if (fileName.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileName must be 255 characters or less");
        }

        AppRelease release = new AppRelease();
        release.setPlatform(platform);
        release.setVersionName(versionName.trim());
        release.setVersionCode(versionCode);
        release.setMinSdk(minSdk != null ? minSdk : 33);
        release.setReleaseNotes(releaseNotes);
        release.setPublished(published);
        release.setFileName(sanitizeFileName(fileName));
        release.setContentType(APK_CONTENT_TYPE);
        release.setFileSize(fileSize);
        release.setDownloadUrl(downloadUrl);
        release.setChecksumSha256(checksumSha256);
        release.setUploadedBy(uploader);

        AppRelease saved = releaseRepository.save(release);
        log.info("app_release_uploaded platform={} versionName={} versionCode={} sizeBytes={} releaseId={}",
                platform, saved.getVersionName(), saved.getVersionCode(), saved.getFileSize(), saved.getId());
        return saved;
    }

    @Transactional
    public AppRelease setPublished(UUID id, boolean published) {
        AppRelease release = getById(id);
        release.setPublished(published);
        log.info("app_release_publish_state_changed releaseId={} published={}", id, published);
        return releaseRepository.save(release);
    }

    @Transactional
    public void delete(UUID id) {
        AppRelease release = getById(id);
        releaseRepository.delete(release);
        log.info("app_release_deleted releaseId={} versionCode={}", id, release.getVersionCode());
    }

    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) return "mesha.apk";
        // Strip any path segments, then restrict to a safe charset so the value cannot
        // break out of the Content-Disposition header (quotes / CR / LF / control chars).
        String name = original.replaceAll(".*[/\\\\]", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() ? "mesha.apk" : name;
    }
}
