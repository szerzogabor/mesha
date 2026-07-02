package com.mesha.api.controller;

import com.mesha.api.dto.AppReleaseDto;
import com.mesha.api.model.AppPlatform;
import com.mesha.api.model.AppRelease;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.AppReleaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Distribution endpoints for native client releases (Android APKs).
 *
 * <p>Read endpoints ({@code GET .../latest}, list) are public — they are registered
 * as {@code permitAll()} in {@code SecurityConfig} so the marketing site and the
 * in-app updater can reach them without a Clerk session. The APK binary itself is
 * hosted externally (see {@link AppReleaseDto#downloadUrl()}) — this service never
 * buffers the APK bytes. Mutating endpoints require a platform admin.
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

    /** Admin-only list including unpublished releases. */
    @GetMapping("/admin/{platform}")
    @PreAuthorize("@platformSecurity.isPlatformAdmin(authentication)")
    public ResponseEntity<List<AppReleaseDto>> listAll(@PathVariable String platform) {
        List<AppReleaseDto> dtos = releaseService.listAll(parsePlatform(platform))
                .stream().map(AppReleaseDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Publishes a new APK build's metadata. Allowed for platform admins (Clerk session)
     * and for CI, which authenticates with the long-lived {@code relpub_} token since a
     * human admin's Clerk session JWT is too short-lived to use from a build pipeline.
     * The caller uploads the APK itself to external hosting (a GitHub Release asset)
     * first and passes the resulting {@code downloadUrl} here — this endpoint never
     * receives the binary.
     */
    @PostMapping
    @PreAuthorize("@platformSecurity.isPlatformAdmin(authentication) or hasAuthority('ROLE_CI_RELEASE_PUBLISHER')")
    public ResponseEntity<AppReleaseDto> upload(
            @RequestParam(value = "platform", defaultValue = "ANDROID") String platform,
            @RequestParam("versionName") String versionName,
            @RequestParam("versionCode") int versionCode,
            @RequestParam(value = "minSdk", required = false) Integer minSdk,
            @RequestParam(value = "releaseNotes", required = false) String releaseNotes,
            @RequestParam(value = "published", defaultValue = "true") boolean published,
            @RequestParam("fileName") String fileName,
            @RequestParam("fileSize") long fileSize,
            @RequestParam("checksumSha256") String checksumSha256,
            @RequestParam("downloadUrl") String downloadUrl,
            @CurrentUser(required = false) User user) {
        AppRelease release = releaseService.upload(
                parsePlatform(platform), versionName, versionCode, minSdk, releaseNotes, published,
                fileName, fileSize, checksumSha256, downloadUrl, user);
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

    private AppPlatform parsePlatform(String platform) {
        try {
            return AppPlatform.valueOf(platform.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown platform: " + platform);
        }
    }
}
