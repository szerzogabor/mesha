package com.mesha.mobile.localai.model

/**
 * Progress of an in-flight model install, emitted by the download manager and repository.
 *
 * The flow is: [Connecting] → [Downloading]* → [Verifying] → [Completed], or [Failed] at any
 * point. Cancellation surfaces as coroutine cancellation, not a [Failed] state, so a paused
 * download leaves its partial file in place for resume.
 */
sealed interface DownloadState {

    data object Connecting : DownloadState

    /**
     * @param bytesDownloaded bytes written so far (including any resumed-from prefix)
     * @param totalBytes      expected total, or `-1` if the server did not report a length
     */
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState {
        /** Completion in `0f..1f`, or `null` when the total size is unknown. */
        val fraction: Float?
            get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    data object Verifying : DownloadState

    data class Completed(val model: InstalledModel) : DownloadState

    data class Failed(val reason: DownloadError, val message: String) : DownloadState
}

/** Categorised, user-presentable reasons an install can fail. */
enum class DownloadError {
    NETWORK_UNAVAILABLE,
    DOWNLOAD_TIMEOUT,
    DOWNLOAD_INTERRUPTED,
    INSUFFICIENT_STORAGE,
    CHECKSUM_MISMATCH,
    CORRUPTED_INSTALLATION,
    UNKNOWN,
}
