package com.mesha.api.dto;

import com.mesha.api.model.AppPlatform;
import com.mesha.api.model.AppRelease;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppReleaseDtoTest {

    @Test
    void from_fallsBackToEmptyStringForLegacyReleasesWithoutDownloadUrl() {
        AppRelease release = new AppRelease();
        release.setPlatform(AppPlatform.ANDROID);
        release.setVersionName("1.0.0");
        release.setVersionCode(1);
        release.setFileName("mesha.apk");
        release.setChecksumSha256("a".repeat(64));
        release.setDownloadUrl(null);

        AppReleaseDto dto = AppReleaseDto.from(release);

        assertThat(dto.downloadUrl()).isEqualTo("");
    }

    @Test
    void from_passesThroughDownloadUrlWhenPresent() {
        AppRelease release = new AppRelease();
        release.setPlatform(AppPlatform.ANDROID);
        release.setVersionName("1.0.0");
        release.setVersionCode(1);
        release.setFileName("mesha.apk");
        release.setChecksumSha256("a".repeat(64));
        release.setDownloadUrl("https://github.com/szerzogabor/mesha/releases/download/android-1/mesha.apk");

        AppReleaseDto dto = AppReleaseDto.from(release);

        assertThat(dto.downloadUrl())
                .isEqualTo("https://github.com/szerzogabor/mesha/releases/download/android-1/mesha.apk");
    }
}
