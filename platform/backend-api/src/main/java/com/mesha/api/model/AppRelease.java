package com.mesha.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A published, downloadable native client build (an Android APK today).
 *
 * <p>Releases are platform-wide rather than workspace-scoped: every user downloads
 * the same official binary from the marketing site / in-app updater. The APK bytes
 * are stored as {@code bytea} to avoid an external object-store dependency at the
 * current scale, mirroring {@link IssueAttachment}.
 *
 * <p>{@code versionCode} is the monotonic integer the Android client compares against
 * its own {@code BuildConfig.VERSION_CODE} to decide whether an update is available;
 * {@code versionName} is the human-facing semantic version shown in the UI.
 */
@Entity
@Table(name = "app_releases",
       indexes = {
           @Index(name = "idx_app_releases_platform_published", columnList = "platform, published"),
           @Index(name = "idx_app_releases_version_code", columnList = "platform, version_code")
       })
public class AppRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 31)
    private AppPlatform platform = AppPlatform.ANDROID;

    @Column(name = "version_name", nullable = false, length = 63)
    private String versionName;

    @Column(name = "version_code", nullable = false)
    private int versionCode;

    @Column(name = "release_notes", columnDefinition = "text")
    private String releaseNotes;

    @Column(name = "min_sdk", nullable = false)
    private int minSdk = 33; // Android 13

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 127)
    private String contentType = "application/vnd.android.package-archive";

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    // Stored as bytea (see V49 migration). Matches IssueAttachment: a plain byte[]
    // maps to bytea under Hibernate/Postgres; @Lob would map to OID and fail validate.
    @Column(name = "content", nullable = false)
    private byte[] content;

    /** SHA-256 of the APK bytes, surfaced so clients can verify the download. */
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "published", nullable = false)
    private boolean published = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public AppPlatform getPlatform() { return platform; }
    public void setPlatform(AppPlatform platform) { this.platform = platform; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public int getVersionCode() { return versionCode; }
    public void setVersionCode(int versionCode) { this.versionCode = versionCode; }
    public String getReleaseNotes() { return releaseNotes; }
    public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }
    public int getMinSdk() { return minSdk; }
    public void setMinSdk(int minSdk) { this.minSdk = minSdk; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public User getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(User uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
