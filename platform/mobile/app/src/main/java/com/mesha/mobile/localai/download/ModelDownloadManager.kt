package com.mesha.mobile.localai.download

import com.mesha.mobile.localai.model.DownloadError
import com.mesha.mobile.localai.model.DownloadState
import com.mesha.mobile.localai.model.InstalledModel
import com.mesha.mobile.localai.model.LocalAiModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import com.mesha.mobile.localai.storage.ModelStorageManager.Companion.PART_SUFFIX
import com.mesha.mobile.localai.util.Sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads model artifacts to app-specific storage with resume, progress, cancellation,
 * checksum verification and atomic install.
 *
 * Engine-agnostic by design — it moves bytes and verifies a SHA-256; it neither knows nor
 * cares whether the artifact is a MediaPipe `.task`, an ONNX graph or a GGUF file.
 *
 * Resume strategy: bytes stream into `<file>.part`. On a retry/resume we send an HTTP
 * `Range: bytes=<existing>-` header and append; a `206 Partial Content` response continues
 * where we left off, a `200 OK` (server ignored the range) restarts cleanly. The completed
 * file is hashed end-to-end and only then moved into place, so a partial or corrupt download
 * is never mistaken for an install.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    private val baseClient: OkHttpClient,
    private val storage: ModelStorageManager,
) {
    // Model files are large; allow generous read timeouts independent of the API client.
    private val client: OkHttpClient = baseClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Downloads and installs [model], emitting progress. Honours coroutine cancellation: a
     * cancelled collection stops the transfer but leaves the `.part` file for a later resume.
     */
    fun download(model: LocalAiModel): Flow<DownloadState> = flow {
        emit(DownloadState.Connecting)

        if (model.sizeBytes > 0 && !storage.hasSpaceFor(model.sizeBytes)) {
            emit(
                DownloadState.Failed(
                    DownloadError.INSUFFICIENT_STORAGE,
                    "Not enough free space to install ${model.name}.",
                ),
            )
            return@flow
        }

        val dir = storage.modelDir(model.id)
        val partFile = File(dir, model.fileName + PART_SUFFIX)
        val finalFile = File(dir, model.fileName)

        try {
            streamToPartFile(model, partFile) { downloaded, total ->
                emit(DownloadState.Downloading(downloaded, total))
            }

            emit(DownloadState.Verifying)
            val actual = Sha256.ofFile(partFile)
            if (!Sha256.matches(model.sha256, actual)) {
                partFile.delete()
                emit(
                    DownloadState.Failed(
                        DownloadError.CHECKSUM_MISMATCH,
                        "Integrity check failed for ${model.name}. The download was removed; please retry.",
                    ),
                )
                return@flow
            }

            // Atomic-ish install: move the verified part file into place.
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            val installed = InstalledModel(
                id = model.id,
                name = model.name,
                provider = model.provider,
                version = model.version,
                engine = model.engine,
                fileName = model.fileName,
                sizeBytes = finalFile.length(),
                sha256 = actual,
                installedAtEpochMs = System.currentTimeMillis(),
            )
            storage.writeMetadata(installed)
            emit(DownloadState.Completed(installed))
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Pause: keep the .part file so the next attempt resumes. Re-throw so the
            // collecting coroutine observes the cancellation.
            throw cancellation
        } catch (e: Exception) {
            emit(DownloadState.Failed(classify(e), e.message ?: "Download failed."))
        }
    }.flowOn(Dispatchers.IO)

    /** Streams the body into [partFile], resuming from its current length when possible. */
    private suspend inline fun streamToPartFile(
        model: LocalAiModel,
        partFile: File,
        crossinline onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ) {
        val existing = if (partFile.exists()) partFile.length() else 0L
        val requestBuilder = Request.Builder().url(model.downloadUrl)
        if (existing > 0) {
            requestBuilder.header("Range", "bytes=$existing-")
        }

        // execute()/read() are blocking; cancel the Call when the coroutine is cancelled so a
        // pause/cancel unblocks the IO thread immediately instead of waiting for a timeout.
        val call = client.newCall(requestBuilder.build())
        val cancelOnCompletion = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body")

                // 206 => server honoured the range and we append; otherwise restart from scratch.
                val resumed = response.code == 206 && existing > 0
                val startOffset = if (resumed) existing else 0L
                val reportedLength = body.contentLength()
                val total = when {
                    model.sizeBytes > 0 -> model.sizeBytes
                    reportedLength > 0 -> startOffset + reportedLength
                    else -> -1L
                }

                partFile.parentFile?.mkdirs()
                val output = java.io.RandomAccessFile(partFile, "rw")
                output.use { raf ->
                    if (resumed) raf.seek(existing) else raf.setLength(0)
                    var downloaded = startOffset
                    // Throttle progress: a multi-GB model would otherwise emit hundreds of
                    // thousands of updates and flood the UI with recompositions.
                    var lastProgressMs = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    body.byteStream().use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            raf.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastProgressMs >= PROGRESS_THROTTLE_MS) {
                                onProgress(downloaded, total)
                                lastProgressMs = now
                            }
                        }
                    }
                    // Always emit the final, exact progress once the transfer completes.
                    onProgress(downloaded, total)
                }
            }
        } finally {
            cancelOnCompletion?.dispose()
        }
    }

    private fun classify(e: Throwable): DownloadError = when (e) {
        is UnknownHostException -> DownloadError.NETWORK_UNAVAILABLE
        is InterruptedIOException -> DownloadError.DOWNLOAD_TIMEOUT
        is IOException -> DownloadError.DOWNLOAD_INTERRUPTED
        else -> DownloadError.UNKNOWN
    }

    private companion object {
        /** Minimum gap between progress emissions, to avoid flooding the UI. */
        const val PROGRESS_THROTTLE_MS = 100L
    }
}
