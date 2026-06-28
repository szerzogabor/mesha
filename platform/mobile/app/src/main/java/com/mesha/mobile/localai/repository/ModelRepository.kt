package com.mesha.mobile.localai.repository

import com.mesha.mobile.localai.catalog.ModelCatalogRepository
import com.mesha.mobile.localai.download.ModelDownloadManager
import com.mesha.mobile.localai.model.CatalogEntry
import com.mesha.mobile.localai.model.DownloadState
import com.mesha.mobile.localai.model.InstalledModel
import com.mesha.mobile.localai.model.LocalAiModel
import com.mesha.mobile.localai.model.ModelStatus
import com.mesha.mobile.localai.storage.ModelStorageManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** A catalog model whose installed version is older than the catalog version. */
data class ModelUpdate(
    val model: LocalAiModel,
    val installed: InstalledModel,
)

/**
 * High-level Local AI API consumed by ViewModels. Coordinates the catalog, download manager
 * and storage so callers work in terms of "models" rather than HTTP, files and checksums.
 */
@Singleton
class ModelRepository @Inject constructor(
    private val catalog: ModelCatalogRepository,
    private val downloadManager: ModelDownloadManager,
    private val storage: ModelStorageManager,
) {
    /** Catalog models joined with their on-device install status. */
    suspend fun availableModels(forceRefresh: Boolean = false): Result<List<CatalogEntry>> =
        catalog.getCatalog(forceRefresh).map { models ->
            models.map { model -> CatalogEntry(model, statusOf(model)) }
        }

    /** Everything currently installed on the device. */
    fun installedModels(): List<InstalledModel> = storage.installedModels()

    /**
     * Downloads, verifies and installs [model], emitting progress. Cancelling the collecting
     * coroutine pauses the download (the partial file is kept for resume).
     */
    fun installModel(model: LocalAiModel): Flow<DownloadState> = downloadManager.download(model)

    /** Removes an installed model and its files. */
    fun removeModel(id: String): Boolean = storage.delete(id)

    /**
     * Models whose installed version differs from the catalog version. Refreshes the catalog
     * first so the comparison reflects the latest backend state.
     */
    suspend fun checkForUpdates(): Result<List<ModelUpdate>> =
        catalog.getCatalog(forceRefresh = true).map { models ->
            models.mapNotNull { model ->
                val installed = storage.readMetadata(model.id) ?: return@mapNotNull null
                if (installed.version != model.version) ModelUpdate(model, installed) else null
            }
        }

    private fun statusOf(model: LocalAiModel): ModelStatus {
        val installed = storage.readMetadata(model.id) ?: return ModelStatus.NotInstalled
        return if (installed.version != model.version) {
            ModelStatus.UpdateAvailable(installed)
        } else {
            ModelStatus.Installed(installed)
        }
    }
}
