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

    private fun release(versionCode: Int) = AppReleaseDto(
        id = "r1",
        platform = "ANDROID",
        versionName = "9.9.9",
        versionCode = versionCode,
        fileName = "mesha.apk",
        fileSize = 1000,
        checksumSha256 = "abc",
        downloadUrl = "/api/releases/r1/download",
    )
}
