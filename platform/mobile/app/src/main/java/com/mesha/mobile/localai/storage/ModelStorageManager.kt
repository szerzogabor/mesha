package com.mesha.mobile.localai.storage

import android.content.Context
import com.mesha.mobile.localai.model.InstalledModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns on-device model storage. Everything lives under app-specific external files
 * (`Android/data/com.mesha.mobile/files/models/`) so no runtime storage permission is ever
 * required and the OS reclaims the space on uninstall.
 *
 * Layout per the feature spec:
 * ```
 * models/
 *   <id>/
 *     <model file, e.g. model.task>
 *     metadata.json   ← [InstalledModel], the source of truth for "installed"
 *     checksum.txt    ← verified SHA-256
 * ```
 *
 * The filesystem — not a database — is the source of truth, so installed models survive app
 * restarts automatically.
 */
@Singleton
class ModelStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    /** Root directory holding every installed model's folder. */
    fun modelsRoot(): File =
        (context.getExternalFilesDir("models") ?: File(context.filesDir, "models"))
            .also { it.mkdirs() }

    /** The directory for a single model id (created on demand). */
    fun modelDir(id: String): File = File(modelsRoot(), id).also { it.mkdirs() }

    private fun metadataFile(id: String): File = File(modelDir(id), METADATA_FILE)
    private fun checksumFile(id: String): File = File(modelDir(id), CHECKSUM_FILE)

    /** The model artifact file for an installed model, or null if not present. */
    fun modelFile(model: InstalledModel): File? =
        File(modelDir(model.id), model.fileName).takeIf { it.exists() && it.length() > 0 }

    fun isInstalled(id: String): Boolean = readMetadata(id) != null

    /** Reads a single installed model's metadata, or null if it is not installed/corrupted. */
    fun readMetadata(id: String): InstalledModel? {
        val file = metadataFile(id)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<InstalledModel>(file.readText()) }
            .getOrNull()
            ?.takeIf {
                // A present-but-empty artifact (interrupted/truncated download) is not installed.
                val artifact = File(modelDir(id), it.fileName)
                artifact.exists() && artifact.length() > 0
            }
    }

    /** All currently installed models, discovered by scanning the models root. */
    fun installedModels(): List<InstalledModel> =
        modelsRoot().listFiles { f -> f.isDirectory }
            ?.mapNotNull { readMetadata(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()

    /** Persists [model]'s metadata and checksum, marking it installed. */
    fun writeMetadata(model: InstalledModel) {
        metadataFile(model.id).writeText(json.encodeToString(InstalledModel.serializer(), model))
        checksumFile(model.id).writeText(model.sha256)
    }

    /** Deletes an installed model and all of its files. Returns true if anything was removed. */
    fun delete(id: String): Boolean {
        val dir = File(modelsRoot(), id)
        return dir.exists() && dir.deleteRecursively()
    }

    /** Total bytes used by a single installed model's directory. */
    fun diskUsageBytes(id: String): Long = File(modelsRoot(), id).walkBottomUp()
        .filter { it.isFile }
        .sumOf { it.length() }

    /** Total bytes used by all installed models. */
    fun totalDiskUsageBytes(): Long = modelsRoot().walkBottomUp()
        .filter { it.isFile }
        .sumOf { it.length() }

    /** Free space (bytes) available on the volume backing model storage. */
    fun availableStorageBytes(): Long = modelsRoot().usableSpace

    /**
     * True when there is room to install a download of [requiredBytes], keeping a small safety
     * margin so the device is never driven completely out of space.
     */
    fun hasSpaceFor(requiredBytes: Long): Boolean =
        // Subtract the margin from the available side so a huge (corrupt/malicious) requiredBytes
        // can't overflow the addition into a negative — which would wrongly report space.
        requiredBytes >= 0 && availableStorageBytes() - SAFETY_MARGIN_BYTES >= requiredBytes

    companion object {
        const val METADATA_FILE = "metadata.json"
        const val CHECKSUM_FILE = "checksum.txt"
        const val PART_SUFFIX = ".part"

        /** Headroom kept free after a download so the system stays healthy. */
        const val SAFETY_MARGIN_BYTES = 200L * 1024 * 1024 // 200 MB
    }
}
