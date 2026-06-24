package com.mesha.mobile.domain.ai

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the provider behavior that does not require the MediaPipe native runtime:
 * availability reporting and the model-absent failure path. Full inference is exercised
 * by instrumented tests on a device with a model present.
 */
class GemmaLocalAiProviderTest {

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun isAvailable_reflectsModelInstallation() = runTest {
        val modelManager = mockk<GemmaModelManager>()
        every { modelManager.isModelInstalled() } returns false
        val provider = GemmaLocalAiProvider(context, modelManager)

        assertFalse(provider.isAvailable())
    }

    @Test
    fun generateIssueDraft_throwsModelNotAvailable_whenModelMissing() = runTest {
        val modelManager = mockk<GemmaModelManager>()
        every { modelManager.resolveModelFile() } returns null
        val provider = GemmaLocalAiProvider(context, modelManager)

        assertThrows(LocalAiException.ModelNotAvailable::class.java) {
            kotlinx.coroutines.runBlocking {
                provider.generateIssueDraft(GenerateIssueRequest("Add a feature"))
            }
        }
    }

    @Test
    fun providerMetadata_isStable() {
        val provider = GemmaLocalAiProvider(context, mockk(relaxed = true))
        assertEquals("gemma", provider.id)
        assertTrue(provider.displayName.contains("Gemma"))
    }
}
