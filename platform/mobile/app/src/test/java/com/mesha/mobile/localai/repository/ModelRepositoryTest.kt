package com.mesha.mobile.localai.repository

import com.mesha.mobile.localai.catalog.ModelCatalogRepository
import com.mesha.mobile.localai.download.ModelDownloadManager
import com.mesha.mobile.localai.model.InstalledModel
import com.mesha.mobile.localai.model.LocalAiModel
import com.mesha.mobile.localai.model.ModelStatus
import com.mesha.mobile.localai.storage.ModelStorageManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRepositoryTest {

    private val catalog = mockk<ModelCatalogRepository>()
    private val downloadManager = mockk<ModelDownloadManager>(relaxed = true)
    private val storage = mockk<ModelStorageManager>()

    private val repository = ModelRepository(catalog, downloadManager, storage)

    private fun model(id: String, version: String = "1.0") = LocalAiModel(
        id = id, name = id, provider = "Google", source = "huggingface", version = version,
        engine = "mediapipe", fileName = "$id.task", sizeBytes = 100, sha256 = "",
        downloadUrl = "https://h/$id.task",
    )

    private fun installed(id: String, version: String) = InstalledModel(
        id = id, name = id, provider = "Google", version = version, engine = "mediapipe",
        fileName = "$id.task", sizeBytes = 100, sha256 = "abc", installedAtEpochMs = 1L,
    )

    @Test
    fun availableModels_reportsNotInstalled() = runTest {
        coEvery { catalog.getCatalog(any()) } returns Result.success(listOf(model("gemma-3n-e2b")))
        every { storage.readMetadata("gemma-3n-e2b") } returns null

        val entry = repository.availableModels().getOrThrow().single()
        assertTrue(entry.status is ModelStatus.NotInstalled)
    }

    @Test
    fun availableModels_reportsInstalledWhenVersionsMatch() = runTest {
        coEvery { catalog.getCatalog(any()) } returns Result.success(listOf(model("gemma-3n-e2b", "1.0")))
        every { storage.readMetadata("gemma-3n-e2b") } returns installed("gemma-3n-e2b", "1.0")

        val entry = repository.availableModels().getOrThrow().single()
        assertTrue(entry.status is ModelStatus.Installed)
    }

    @Test
    fun availableModels_reportsUpdateAvailableWhenCatalogIsNewer() = runTest {
        coEvery { catalog.getCatalog(any()) } returns Result.success(listOf(model("gemma-3n-e2b", "2.0")))
        every { storage.readMetadata("gemma-3n-e2b") } returns installed("gemma-3n-e2b", "1.0")

        val entry = repository.availableModels().getOrThrow().single()
        assertTrue(entry.status is ModelStatus.UpdateAvailable)
    }

    @Test
    fun checkForUpdates_returnsOnlyOutdatedInstalledModels() = runTest {
        coEvery { catalog.getCatalog(forceRefresh = true) } returns Result.success(
            listOf(model("gemma-3n-e2b", "2.0"), model("gemma-3n-e4b", "1.0")),
        )
        every { storage.readMetadata("gemma-3n-e2b") } returns installed("gemma-3n-e2b", "1.0")
        every { storage.readMetadata("gemma-3n-e4b") } returns installed("gemma-3n-e4b", "1.0")

        val updates = repository.checkForUpdates().getOrThrow()
        assertEquals(1, updates.size)
        assertEquals("gemma-3n-e2b", updates.single().model.id)
    }

    @Test
    fun removeModel_delegatesToStorage() {
        every { storage.delete("gemma-3n-e2b") } returns true
        assertTrue(repository.removeModel("gemma-3n-e2b"))
    }
}
