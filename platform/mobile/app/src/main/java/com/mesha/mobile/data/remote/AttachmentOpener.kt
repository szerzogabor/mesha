package com.mesha.mobile.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an issue attachment to the app cache and opens it with the system viewer.
 *
 * The attachment content endpoint is auth-protected, so it must be fetched through the
 * shared [OkHttpClient] (which carries the Clerk bearer token) rather than handed to an
 * external browser. The downloaded file is exposed via the existing [FileProvider] so the
 * chosen viewer app can read it.
 */
@Singleton
class AttachmentOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    /**
     * Fetch [url] into the cache and launch a viewer for it. Runs the download on IO and
     * throws on any network / HTTP failure so the caller can surface an error.
     */
    suspend fun open(url: String, fileName: String, contentType: String?) {
        val file = download(url, fileName)
        withContext(Dispatchers.Main) { launchViewer(file, contentType) }
    }

    /**
     * Download [url] into the cache. The OkHttp call is cancelled if the surrounding
     * coroutine is cancelled, the copy loop cooperatively checks for cancellation, and any
     * failure/cancellation deletes the partial file so a corrupt cache entry isn't left behind.
     */
    private suspend fun download(url: String, fileName: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val outFile = File(dir, safeFileName(fileName))
            val request = Request.Builder().url(url).build()
            val call = okHttpClient.newCall(request)
            val registration = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                    val body = response.body ?: error("Empty response body")
                    body.byteStream().use { input ->
                        outFile.outputStream().use { out ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }
                outFile
            } catch (e: Throwable) {
                outFile.delete()
                throw e
            } finally {
                registration?.dispose()
            }
        }

    private fun launchViewer(file: File, contentType: String?) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        // Prefer the server-declared type; fall back to the file extension so viewers that
        // reject a generic */* still get a concrete hint.
        val mimeType = contentType?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    /**
     * Strip path separators (so an attachment name can't escape the cache directory) and
     * replace any remaining characters that filesystems or viewers may choke on. The
     * extension is preserved so MIME resolution still works.
     */
    private fun safeFileName(fileName: String): String {
        val stripped = fileName.substringAfterLast('/').substringAfterLast('\\')
        return stripped.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "attachment" }
    }
}
