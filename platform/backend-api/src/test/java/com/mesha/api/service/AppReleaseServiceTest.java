package com.mesha.api.service;

import com.mesha.api.model.AppPlatform;
import com.mesha.api.model.AppRelease;
import com.mesha.api.model.User;
import com.mesha.api.repository.AppReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppReleaseServiceTest {

    @Mock private AppReleaseRepository releaseRepository;

    private AppReleaseService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new AppReleaseService(releaseRepository, 200L * 1024 * 1024);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ---- upload ----

    private static final String APK_URL = "https://github.com/szerzogabor/mesha/releases/download/android-5/mesha.apk";

    @Test
    void upload_savesReleaseWithSuppliedFields() {
        when(releaseRepository.existsByPlatformAndVersionCode(AppPlatform.ANDROID, 5)).thenReturn(false);
        when(releaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppRelease result = service.upload(AppPlatform.ANDROID, "1.2.0", 5, 34,
                "Notes", true, "mesha.apk", 2048, "a".repeat(64), APK_URL, new User());

        ArgumentCaptor<AppRelease> captor = ArgumentCaptor.forClass(AppRelease.class);
        verify(releaseRepository).save(captor.capture());
        AppRelease saved = captor.getValue();
        assertThat(saved.getVersionName()).isEqualTo("1.2.0");
        assertThat(saved.getVersionCode()).isEqualTo(5);
        assertThat(saved.getMinSdk()).isEqualTo(34);
        assertThat(saved.getFileSize()).isEqualTo(2048);
        assertThat(saved.getContentType()).isEqualTo("application/vnd.android.package-archive");
        assertThat(saved.getDownloadUrl()).isEqualTo(APK_URL);
        assertThat(saved.getChecksumSha256()).hasSize(64);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void upload_defaultsMinSdkTo33WhenNull() {
        when(releaseRepository.existsByPlatformAndVersionCode(any(), anyInt())).thenReturn(false);
        when(releaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true,
                "mesha.apk", 16, "a".repeat(64), APK_URL, new User());

        ArgumentCaptor<AppRelease> captor = ArgumentCaptor.forClass(AppRelease.class);
        verify(releaseRepository).save(captor.capture());
        assertThat(captor.getValue().getMinSdk()).isEqualTo(33);
    }

    @Test
    void upload_rejectsNonApkFileName() {
        when(releaseRepository.existsByPlatformAndVersionCode(any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true,
                "evil.exe", 16, "a".repeat(64), APK_URL, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void upload_rejectsNonHttpsDownloadUrl() {
        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true,
                "mesha.apk", 16, "a".repeat(64), "http://example.com/mesha.apk", new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_rejectsOversizedDownloadUrl() {
        String tooLong = "https://example.com/" + "a".repeat(2048);
        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true,
                "mesha.apk", 16, "a".repeat(64), tooLong, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_rejectsWrongLengthChecksum() {
        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true,
                "mesha.apk", 16, "not-a-sha256", APK_URL, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_rejectsOversizedFileName() {
        String tooLong = "a".repeat(253) + ".apk";
        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true,
                tooLong, 16, "a".repeat(64), APK_URL, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_rejectsNonPositiveFileSize() {
        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true,
                "mesha.apk", 0, "a".repeat(64), APK_URL, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_rejectsDuplicateVersionCode() {
        when(releaseRepository.existsByPlatformAndVersionCode(AppPlatform.ANDROID, 5)).thenReturn(true);

        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 5, null, null, true,
                "mesha.apk", 16, "a".repeat(64), APK_URL, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void upload_rejectsNonPositiveVersionCode() {
        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 0, null, null, true,
                "mesha.apk", 16, "a".repeat(64), APK_URL, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- getLatest ----

    @Test
    void getLatest_returnsRepositoryResult() {
        AppRelease release = new AppRelease();
        when(releaseRepository.findFirstByPlatformAndPublishedTrueOrderByVersionCodeDesc(AppPlatform.ANDROID))
                .thenReturn(Optional.of(release));

        assertThat(service.getLatest(AppPlatform.ANDROID)).isSameAs(release);
    }

    @Test
    void getLatest_throwsWhenNoneAvailable() {
        when(releaseRepository.findFirstByPlatformAndPublishedTrueOrderByVersionCodeDesc(AppPlatform.ANDROID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatest(AppPlatform.ANDROID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- delete ----

    @Test
    void delete_throwsWhenReleaseMissing() {
        UUID id = UUID.randomUUID();
        when(releaseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
