package com.mesha.api.controller;

import com.mesha.api.dto.AppReleaseDto;
import com.mesha.api.model.AppPlatform;
import com.mesha.api.model.AppRelease;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.AppReleaseService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Distribution endpoints for native client releases (Android APKs).
 *
 * <p>Read endpoints ({@code GET .../latest}, list, and download) are public — they
 * are registered as {@code permitAll()} in {@code SecurityConfig} so the marketing
 * site and the in-app updater can reach them without a Clerk session. Mutating
 * endpoints require a platform admin.
 */
@RestController
@RequestMapping("/api/releases")
public class AppReleaseController {

    private final AppReleaseService releaseService;

    public AppReleaseController(AppReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    /** Latest published release for a platform — drives both the download page and update checks. */
    @GetMapping("/{platform}/latest")
    public ResponseEntity<AppReleaseDto> latest(@PathVariable String platform) {
        AppRelease release = releaseService.getLatest(parsePlatform(platform));
        return ResponseEntity.ok(AppReleaseDto.from(release));
    }

    /** All published releases for a platform, newest first (release-notes history). */
    @GetMapping("/{platform}")
    public ResponseEntity<List<AppReleaseDto>> listPublished(@PathVariable String platform) {
        List<AppReleaseDto> dtos = releaseService.listPublished(parsePlatform(platform))
                .stream().map(AppReleaseDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    /** Convenience redirect-free download of the latest published APK for a platform. */
    @GetMapping("/{platform}/latest/download")
    public ResponseEntity<byte[]> downloadLatest(@PathVariable String platform) {
        return streamApk(releaseService.getLatest(parsePlatform(platform)));
    }

    /** Stable download URL for a specific release id (returned in {@link AppReleaseDto#downloadUrl()}). */
    @GetMapping("/{releaseId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID releaseId) {
        return streamApk(releaseService.getById(releaseId));
    }

    /** Admin-only list including unpublished releases. */
    @GetMapping("/admin/{platform}")
    @PreAuthorize("@platformSecurity.isPlatformAdmin(authentication)")
    public ResponseEntity<List<AppReleaseDto>> listAll(@PathVariable String platform) {
        List<AppReleaseDto> dtos = releaseService.listAll(parsePlatform(platform))
                .stream().map(AppReleaseDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Upload of a new APK build. Allowed for platform admins (Clerk session) and for
     * CI, which authenticates with the long-lived {@code relpub_} token since a human
     * admin's Clerk session JWT is too short-lived to use from a build pipeline.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@platformSecurity.isPlatformAdmin(authentication) or hasAuthority('ROLE_CI_RELEASE_PUBLISHER')")
    public ResponseEntity<AppReleaseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "platform", defaultValue = "ANDROID") String platform,
            @RequestParam("versionName") String versionName,
            @RequestParam("versionCode") int versionCode,
            @RequestParam(value = "minSdk", required = false) Integer minSdk,
            @RequestParam(value = "releaseNotes", required = false) String releaseNotes,
            @RequestParam(value = "published", defaultValue = "true") boolean published,
            @CurrentUser(required = false) User user) {
        AppRelease release = releaseService.upload(
                parsePlatform(platform), versionName, versionCode, minSdk, releaseNotes, published, file, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(AppReleaseDto.from(release));
    }

    /** Admin-only publish / unpublish toggle. */
    @PatchMapping("/{releaseId}/published")
    @PreAuthorize("@platformSecurity.isPlatformAdmin(authentication)")
    public ResponseEntity<AppReleaseDto> setPublished(@PathVariable UUID releaseId,
                                                      @RequestParam("published") boolean published) {
        return ResponseEntity.ok(AppReleaseDto.from(releaseService.setPublished(releaseId, published)));
    }

    /** Admin-only delete. */
    @DeleteMapping("/{releaseId}")
    @PreAuthorize("@platformSecurity.isPlatformAdmin(authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID releaseId) {
        releaseService.delete(releaseId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> streamApk(AppRelease release) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + release.getFileName() + "\"")
                .header("X-Checksum-SHA256", release.getChecksumSha256())
                .header("X-App-Version-Code", String.valueOf(release.getVersionCode()))
                .header("X-App-Version-Name", release.getVersionName())
                .contentType(MediaType.parseMediaType(release.getContentType()))
                .body(release.getContent());
    }

    private AppPlatform parsePlatform(String platform) {
        try {
            return AppPlatform.valueOf(platform.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown platform: " + platform);
        }
    }
}
