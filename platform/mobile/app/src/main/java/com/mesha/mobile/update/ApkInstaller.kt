package com.mesha.mobile.update

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
 * Downloads an APK update to the app cache and hands it to the system package installer.
 * On Android 13+ the user confirms the install (REQUEST_INSTALL_PACKAGES); we never
 * install silently. The file is exposed via [FileProvider] so the installer can read it.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    /** Downloads [url] into the cache and returns the file, or throws on failure. */
    suspend fun download(url: String, fileName: String = "mesha-update.apk"): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, fileName)
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                val body = response.body ?: error("Empty response body")
                outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            outFile
        }

    /** Launches the system installer for a previously downloaded APK file. */
    fun install(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
