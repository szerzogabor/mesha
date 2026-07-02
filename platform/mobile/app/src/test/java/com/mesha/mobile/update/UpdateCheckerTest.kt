package com.mesha.mobile.update

import com.mesha.mobile.BuildConfig
import com.mesha.mobile.data.remote.MeshaApi
import com.mesha.mobile.data.remote.dto.AppReleaseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val api = mockk<MeshaApi>()

    @Test
    fun reportsUpdateWhenServerVersionIsNewer() = runTest {
        coEvery { api.getLatestRelease(any()) } returns release(BuildConfig.VERSION_CODE + 1)
        val status = UpdateChecker(api).check()
        assertTrue(status is UpdateStatus.UpdateAvailable)
    }

    @Test
    fun reportsUpToDateWhenServerVersionIsSameOrOlder() = runTest {
        coEvery { api.getLatestRelease(any()) } returns release(BuildConfig.VERSION_CODE)
        assertTrue(UpdateChecker(api).check() is UpdateStatus.UpToDate)
    }

    @Test
    fun reportsUpToDateWhenCheckFails() = runTest {
        coEvery { api.getLatestRelease(any()) } throws RuntimeException("offline")
        assertTrue(UpdateChecker(api).check() is UpdateStatus.UpToDate)
    }

    @Test
    fun downloadUrlPassesThroughAbsoluteUrls() {
        val absolute = "https://github.com/szerzogabor/mesha/releases/download/android-1/mesha.apk"
        val url = UpdateChecker(api).downloadUrl(release(1, downloadUrl = absolute))
        assertTrue(url == absolute)
    }

    @Test
    fun downloadUrlPrependsApiBaseForRelativeUrls() {
        val url = UpdateChecker(api).downloadUrl(release(1, downloadUrl = "/api/releases/r1/download"))
        assertTrue(url == BuildConfig.API_BASE_URL.trimEnd('/') + "/api/releases/r1/download")
    }

    @Test
    fun downloadUrlIsBlankForLegacyReleasesWithoutOne() {
        val url = UpdateChecker(api).downloadUrl(release(1, downloadUrl = ""))
        assertTrue(url.isEmpty())
    }

    private fun release(versionCode: Int, downloadUrl: String = "/api/releases/r1/download") = AppReleaseDto(
        id = "r1",
        platform = "ANDROID",
        versionName = "9.9.9",
        versionCode = versionCode,
        fileName = "mesha.apk",
        fileSize = 1000,
        checksumSha256 = "abc",
        downloadUrl = downloadUrl,
    )
}
