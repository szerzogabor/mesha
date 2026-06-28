package com.mesha.mobile.localai.catalog

import com.mesha.mobile.localai.api.LocalAiApi
import com.mesha.mobile.localai.api.LocalAiModelDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogRepositoryTest {

    private val api = mockk<LocalAiApi>()

    private fun dto(id: String, version: String = "1.0") = LocalAiModelDto(
        id = id, name = id, fileName = "$id.task", downloadUrl = "https://h/$id.task", version = version,
    )

    @Test
    fun getCatalog_mapsDtosToDomain() = runTest {
        coEvery { api.getModels() } returns listOf(dto("gemma-3n-e2b"))
        val repo = ModelCatalogRepository(api)

        val result = repo.getCatalog()

        assertTrue(result.isSuccess)
        assertEquals("gemma-3n-e2b", result.getOrThrow().single().id)
    }

    @Test
    fun getCatalog_usesCacheOnSecondCall() = runTest {
        coEvery { api.getModels() } returns listOf(dto("gemma-3n-e2b"))
        val repo = ModelCatalogRepository(api)

        repo.getCatalog()
        repo.getCatalog() // should be served from cache

        coVerify(exactly = 1) { api.getModels() }
    }

    @Test
    fun getCatalog_forceRefreshHitsApiAgain() = runTest {
        coEvery { api.getModels() } returns listOf(dto("gemma-3n-e2b"))
        val repo = ModelCatalogRepository(api)

        repo.getCatalog()
        repo.getCatalog(forceRefresh = true)

        coVerify(exactly = 2) { api.getModels() }
    }

    @Test
    fun getCatalog_fallsBackToCacheWhenRefreshFails() = runTest {
        coEvery { api.getModels() } returns listOf(dto("gemma-3n-e2b"))
        val repo = ModelCatalogRepository(api)
        repo.getCatalog() // seed cache

        coEvery { api.getModels() } throws RuntimeException("offline")
        val result = repo.getCatalog(forceRefresh = true)

        assertTrue(result.isSuccess)
        assertEquals("gemma-3n-e2b", result.getOrThrow().single().id)
    }

    @Test
    fun getCatalog_propagatesFailureWhenNoCache() = runTest {
        coEvery { api.getModels() } throws RuntimeException("offline")
        val repo = ModelCatalogRepository(api)

        val result = repo.getCatalog()

        assertTrue(result.isFailure)
    }
}
