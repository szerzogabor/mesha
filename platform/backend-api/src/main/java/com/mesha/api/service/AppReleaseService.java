package com.mesha.api.service;

import com.mesha.api.model.AppPlatform;
import com.mesha.api.model.AppRelease;
import com.mesha.api.model.User;
import com.mesha.api.repository.AppReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Manages the lifecycle of downloadable native client releases (Android APKs).
 *
 * <p>Reading (latest / list / download) is public so the marketing site and the
 * in-app updater can consume it without authentication. Uploading and deleting are
 * gated to platform admins by the controller's {@code @PreAuthorize} guard.
 *
 * <p>APK content is streamed directly to PostgreSQL via JDBC ({@code setBinaryStream})
 * to avoid loading the entire binary into heap. Calling {@code file.getBytes()} on a
 * 50–100 MB APK inside a 512 MB container would trigger {@code -XX:+ExitOnOutOfMemoryError}
 * and kill the JVM mid-request.
 */
@Service
public class AppReleaseService {

    private static final Logger log = LoggerFactory.getLogger(AppReleaseService.class);
    private static final String APK_CONTENT_TYPE = "application/vnd.android.package-archive";

    private static final String INSERT_SQL =
            "INSERT INTO app_releases " +
            "(id, platform, version_name, version_code, min_sdk, release_notes, published, " +
            " file_name, content_type, file_size, content, checksum_sha256, uploaded_by, created_at) " +
            "VALUES (?, ?::varchar(31), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final AppReleaseRepository releaseRepository;
    private final JdbcTemplate jdbcTemplate;
    private final long maxApkSizeBytes;

    public AppReleaseService(AppReleaseRepository releaseRepository,
                             JdbcTemplate jdbcTemplate,
                             @Value("${app.releases.max-apk-size-bytes:209715200}") long maxApkSizeBytes) {
        this.releaseRepository = releaseRepository;
        this.jdbcTemplate = jdbcTemplate;
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

    /**
     * Streams the APK content directly to PostgreSQL without loading it into heap.
     * Two sequential reads of the multipart temp file are made: one for the SHA-256
     * digest, one for the JDBC binary stream. Both are cheap disk reads; neither
     * materialises the full byte array in memory.
     */
    @Transactional
    public AppRelease upload(AppPlatform platform,
                             String versionName,
                             int versionCode,
                             Integer minSdk,
                             String releaseNotes,
                             boolean published,
                             MultipartFile file,
                             User uploader) {
        if (!StringUtils.hasText(versionName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionName is required");
        }
        if (versionCode <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionCode must be a positive integer");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "APK file must not be empty");
        }
        if (file.getSize() > maxApkSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "APK exceeds maximum allowed size of " + (maxApkSizeBytes / (1024 * 1024)) + " MB");
        }
        if (releaseRepository.existsByPlatformAndVersionCode(platform, versionCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A release with versionCode " + versionCode + " already exists for " + platform);
        }

        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".apk")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload must be an .apk file");
        }

        try {
            String checksum = checksumFromStream(file.getInputStream());
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            String safeFileName = sanitizeFileName(name);
            int resolvedMinSdk = minSdk != null ? minSdk : 33;
            long fileSize = file.getSize();
            UUID uploaderId = uploader != null ? uploader.getId() : null;

            try (InputStream contentStream = file.getInputStream()) {
                jdbcTemplate.update(conn -> {
                    var ps = conn.prepareStatement(INSERT_SQL);
                    int i = 1;
                    ps.setObject(i++, id);
                    ps.setString(i++, platform.name());
                    ps.setString(i++, versionName.trim());
                    ps.setInt(i++, versionCode);
                    ps.setInt(i++, resolvedMinSdk);
                    ps.setString(i++, releaseNotes);
                    ps.setBoolean(i++, published);
                    ps.setString(i++, safeFileName);
                    ps.setString(i++, APK_CONTENT_TYPE);
                    ps.setLong(i++, fileSize);
                    ps.setBinaryStream(i++, contentStream, fileSize);
                    ps.setString(i++, checksum);
                    ps.setObject(i++, uploaderId);
                    ps.setTimestamp(i++, Timestamp.from(now));
                    return ps;
                });
            }

            AppRelease release = new AppRelease();
            release.setId(id);
            release.setPlatform(platform);
            release.setVersionName(versionName.trim());
            release.setVersionCode(versionCode);
            release.setMinSdk(resolvedMinSdk);
            release.setReleaseNotes(releaseNotes);
            release.setPublished(published);
            release.setFileName(safeFileName);
            release.setContentType(APK_CONTENT_TYPE);
            release.setFileSize(fileSize);
            release.setChecksumSha256(checksum);
            release.setCreatedAt(now);
            if (uploader != null) {
                release.setUploadedBy(uploader);
            }

            log.info("app_release_uploaded platform={} versionName={} versionCode={} sizeBytes={} releaseId={}",
                    platform, release.getVersionName(), versionCode, fileSize, id);
            return release;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read uploaded APK: " + e.getMessage());
        }
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
        String name = original.replaceAll(".*[/\\\\]", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.isBlank() ? "mesha.apk" : name;
    }

    private String checksumFromStream(InputStream is) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (DigestInputStream dis = new DigestInputStream(is, digest)) {
                while (dis.read(buffer) != -1) { /* consume stream to compute digest */ }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
