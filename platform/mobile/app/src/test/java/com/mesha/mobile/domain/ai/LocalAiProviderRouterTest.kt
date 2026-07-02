package com.mesha.mobile.domain.ai

import com.mesha.mobile.localai.model.InstalledModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiProviderRouterTest {

    private fun model(engine: String) = InstalledModel(
        id = "$engine-model",
        name = "Test Model",
        provider = "Google",
        version = "1.0",
        engine = engine,
        fileName = "model.bin",
        sizeBytes = 1_000L,
        sha256 = "abc",
        installedAtEpochMs = 0L,
    )

    @Test
    fun isAvailable_false_whenNoModelInstalled() = runTest {
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns emptyList()
        val router = LocalAiProviderRouter(emptyMap(), storageManager)

        assertFalse(router.isAvailable())
    }

    @Test
    fun isAvailable_false_whenInstalledModelsEngineHasNoBoundProvider() = runTest {
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(model("unknown-engine"))
        val router = LocalAiProviderRouter(emptyMap(), storageManager)

        assertFalse(router.isAvailable())
    }

    @Test
    fun isAvailable_delegatesToMatchingProvider() = runTest {
        val mediapipeProvider = mockk<LocalAiProvider>()
        coEvery { mediapipeProvider.isAvailable() } returns true
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(model("mediapipe"))
        val router = LocalAiProviderRouter(mapOf("mediapipe" to mediapipeProvider), storageManager)

        assertTrue(router.isAvailable())
    }

    @Test
    fun generateIssueDraft_throwsModelNotAvailable_whenNoProviderMatches() = runTest {
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns emptyList()
        val router = LocalAiProviderRouter(emptyMap(), storageManager)

        assertThrows(LocalAiException.ModelNotAvailable::class.java) {
            kotlinx.coroutines.runBlocking {
                router.generateIssueDraft(GenerateIssueRequest("Add a feature"))
            }
        }
    }

    @Test
    fun generateIssueDraft_delegatesToMatchingProvider() = runTest {
        val request = GenerateIssueRequest("Add a feature")
        val expectedDraft = IssueDraft(
            title = "Add a feature",
            description = "desc",
            acceptanceCriteria = emptyList(),
            priority = IssuePriority.MEDIUM,
            labels = emptyList(),
        )
        val litertProvider = mockk<LocalAiProvider>()
        coEvery { litertProvider.generateIssueDraft(request) } returns expectedDraft
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(model("litertlm"))
        val router = LocalAiProviderRouter(mapOf("litertlm" to litertProvider), storageManager)

        assertEquals(expectedDraft, router.generateIssueDraft(request))
    }

    @Test
    fun isAvailable_closesPreviouslyActiveProvider_whenInstalledEngineChanges() = runTest {
        val mediapipeProvider = mockk<LocalAiProvider>(relaxed = true)
        val litertProvider = mockk<LocalAiProvider>(relaxed = true)
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(model("mediapipe"))
        val router = LocalAiProviderRouter(
            mapOf("mediapipe" to mediapipeProvider, "litertlm" to litertProvider),
            storageManager,
        )
        router.isAvailable()

        every { storageManager.installedModels() } returns listOf(model("litertlm"))
        router.isAvailable()

        verify(exactly = 1) { mediapipeProvider.close() }
        verify(exactly = 0) { litertProvider.close() }
    }

    @Test
    fun isAvailable_doesNotCloseProvider_whenInstalledEngineUnchanged() = runTest {
        val mediapipeProvider = mockk<LocalAiProvider>(relaxed = true)
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(model("mediapipe"))
        val router = LocalAiProviderRouter(mapOf("mediapipe" to mediapipeProvider), storageManager)

        router.isAvailable()
        router.isAvailable()

        verify(exactly = 0) { mediapipeProvider.close() }
    }

    @Test
    fun generateChatResponse_throwsModelNotAvailable_whenNoProviderMatches() = runTest {
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns emptyList()
        val router = LocalAiProviderRouter(emptyMap(), storageManager)

        assertThrows(LocalAiException.ModelNotAvailable::class.java) {
            kotlinx.coroutines.runBlocking {
                router.generateChatResponse(
                    listOf(LocalChatMessage(LocalChatMessage.Role.USER, "Hello")),
                )
            }
        }
    }

    @Test
    fun generateChatResponse_delegatesToMatchingProvider() = runTest {
        val history = listOf(LocalChatMessage(LocalChatMessage.Role.USER, "Hello"))
        val provider = mockk<LocalAiProvider>()
        coEvery { provider.generateChatResponse(history) } returns "Hi!"
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(model("mediapipe"))
        val router = LocalAiProviderRouter(mapOf("mediapipe" to provider), storageManager)

        assertEquals("Hi!", router.generateChatResponse(history))
    }
}
