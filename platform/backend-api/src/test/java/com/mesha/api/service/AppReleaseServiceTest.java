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
import org.springframework.mock.web.MockMultipartFile;
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

    @Test
    void upload_savesReleaseWithComputedChecksumAndFields() {
        when(releaseRepository.existsByPlatformAndVersionCode(AppPlatform.ANDROID, 5)).thenReturn(false);
        when(releaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile apk = new MockMultipartFile("file", "mesha.apk",
                "application/vnd.android.package-archive", new byte[2048]);

        AppRelease result = service.upload(AppPlatform.ANDROID, "1.2.0", 5, 34,
                "Notes", true, apk, new User());

        ArgumentCaptor<AppRelease> captor = ArgumentCaptor.forClass(AppRelease.class);
        verify(releaseRepository).save(captor.capture());
        AppRelease saved = captor.getValue();
        assertThat(saved.getVersionName()).isEqualTo("1.2.0");
        assertThat(saved.getVersionCode()).isEqualTo(5);
        assertThat(saved.getMinSdk()).isEqualTo(34);
        assertThat(saved.getFileSize()).isEqualTo(2048);
        assertThat(saved.getContentType()).isEqualTo("application/vnd.android.package-archive");
        // SHA-256 of 2048 zero bytes is a fixed 64-char hex string.
        assertThat(saved.getChecksumSha256()).hasSize(64);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void upload_defaultsMinSdkTo33WhenNull() {
        when(releaseRepository.existsByPlatformAndVersionCode(any(), anyInt())).thenReturn(false);
        when(releaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile apk = new MockMultipartFile("file", "mesha.apk", null, new byte[16]);
        service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true, apk, new User());

        ArgumentCaptor<AppRelease> captor = ArgumentCaptor.forClass(AppRelease.class);
        verify(releaseRepository).save(captor.capture());
        assertThat(captor.getValue().getMinSdk()).isEqualTo(33);
    }

    @Test
    void upload_rejectsNonApkFile() {
        when(releaseRepository.existsByPlatformAndVersionCode(any(), anyInt())).thenReturn(false);
        MockMultipartFile notApk = new MockMultipartFile("file", "evil.exe", null, new byte[16]);

        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true, notApk, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void upload_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "mesha.apk", null, new byte[0]);
        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 1, null, null, true, empty, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_rejectsDuplicateVersionCode() {
        when(releaseRepository.existsByPlatformAndVersionCode(AppPlatform.ANDROID, 5)).thenReturn(true);
        MockMultipartFile apk = new MockMultipartFile("file", "mesha.apk", null, new byte[16]);

        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 5, null, null, true, apk, new User()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void upload_rejectsNonPositiveVersionCode() {
        MockMultipartFile apk = new MockMultipartFile("file", "mesha.apk", null, new byte[16]);
        assertThatThrownBy(() -> service.upload(AppPlatform.ANDROID, "1.0.0", 0, null, null, true, apk, new User()))
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
