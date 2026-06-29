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
 * Covers the provider behavior that does not require the LiteRT-LM native runtime:
 * availability reporting and the model-absent failure path. Full inference is exercised
 * by instrumented tests on a device with a model present, mirroring GemmaLocalAiProviderTest.
 */
class LiteRtLmLocalAiProviderTest {

    private val context = mockk<Context>(relaxed = true)

    @Test
    fun isAvailable_reflectsModelInstallation() = runTest {
        val locator = mockk<LiteRtLmModelLocator>()
        every { locator.isModelInstalled() } returns false
        val provider = LiteRtLmLocalAiProvider(context, locator)

        assertFalse(provider.isAvailable())
    }

    @Test
    fun generateIssueDraft_throwsModelNotAvailable_whenModelMissing() = runTest {
        val locator = mockk<LiteRtLmModelLocator>()
        every { locator.resolveModelFile() } returns null
        val provider = LiteRtLmLocalAiProvider(context, locator)

        assertThrows(LocalAiException.ModelNotAvailable::class.java) {
            kotlinx.coroutines.runBlocking {
                provider.generateIssueDraft(GenerateIssueRequest("Add a feature"))
            }
        }
    }

    @Test
    fun providerMetadata_isStable() {
        val provider = LiteRtLmLocalAiProvider(context, mockk(relaxed = true))
        assertEquals("litertlm", provider.id)
        assertTrue(provider.displayName.contains("LiteRT-LM"))
    }

    @Test
    fun hasSufficientMemoryToLoadLiteRtLmModel_falseWhenSystemReportsLowMemory() {
        assertFalse(
            hasSufficientMemoryToLoadLiteRtLmModel(availableBytes = Long.MAX_VALUE, lowMemory = true, modelFileBytes = 1)
        )
    }

    @Test
    fun hasSufficientMemoryToLoadLiteRtLmModel_falseWhenAvailableBelowModelSizeWithOverhead() {
        val modelBytes = 2_000_000_000L
        assertFalse(
            hasSufficientMemoryToLoadLiteRtLmModel(availableBytes = modelBytes, lowMemory = false, modelFileBytes = modelBytes)
        )
    }

    @Test
    fun hasSufficientMemoryToLoadLiteRtLmModel_trueWhenAvailableCoversModelSizeWithOverhead() {
        val modelBytes = 2_000_000_000L
        val ample = (modelBytes * 2)
        assertTrue(
            hasSufficientMemoryToLoadLiteRtLmModel(availableBytes = ample, lowMemory = false, modelFileBytes = modelBytes)
        )
    }
}
