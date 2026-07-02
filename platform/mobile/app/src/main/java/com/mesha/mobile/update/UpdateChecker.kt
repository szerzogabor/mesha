package com.mesha.mobile.update

import com.mesha.mobile.BuildConfig
import com.mesha.mobile.data.remote.MeshaApi
import com.mesha.mobile.data.remote.dto.AppReleaseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the public Mesha release endpoint for a newer APK. Compares the server's
 * monotonic [AppReleaseDto.versionCode] against this build's [BuildConfig.VERSION_CODE].
 * Network/absent-release failures resolve to [UpdateStatus.UpToDate] so a launch is never
 * blocked by the update check.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val api: MeshaApi,
) {
    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching { api.getLatestRelease("android") }
            .map { release ->
                if (release.versionCode > BuildConfig.VERSION_CODE) {
                    UpdateStatus.UpdateAvailable(release)
                } else {
                    UpdateStatus.UpToDate
                }
            }
            .getOrDefault(UpdateStatus.UpToDate)
    }

    /**
     * Absolute URL to download the APK for [release]. The backend now returns an
     * absolute URL pointing at externally-hosted release binary (a GitHub Release
     * asset), so only prepend the API base when it hasn't already given us one.
     */
    fun downloadUrl(release: AppReleaseDto): String =
        if (release.downloadUrl.startsWith("http")) {
            release.downloadUrl
        } else {
            BuildConfig.API_BASE_URL.trimEnd('/') + release.downloadUrl
        }
}

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class UpdateAvailable(val release: AppReleaseDto) : UpdateStatus
}
