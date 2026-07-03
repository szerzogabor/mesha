package com.mesha.mobile.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

    private suspend fun download(url: String, fileName: String): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val outFile = File(dir, safeFileName(fileName))
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                val body = response.body ?: error("Empty response body")
                outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            outFile
        }

    private fun launchViewer(file: File, contentType: String?) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentType?.takeIf { it.isNotBlank() } ?: "*/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    /** Strip path separators so an attachment name can't escape the cache directory. */
    private fun safeFileName(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { "attachment" }
}
