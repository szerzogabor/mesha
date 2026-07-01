package com.mesha.mobile.domain.ai

import android.app.ActivityManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun generateIssueDraft_throwsUnsupportedModel_whenResolvedFileIsNotATaskBundle() = runTest {
        val bogusFile = File.createTempFile("qwen", ".task")
        bogusFile.deleteOnExit()
        bogusFile.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))

        // Stub plenty of free memory so the pre-existing memory guard doesn't short-circuit
        // before the format check under test ever runs.
        val activityManager = mockk<ActivityManager>()
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = firstArg<ActivityManager.MemoryInfo>()
            info.availMem = Long.MAX_VALUE / 2
            info.lowMemory = false
        }
        val plentifulContext = mockk<Context>(relaxed = true)
        every { plentifulContext.getSystemService(ActivityManager::class.java) } returns activityManager

        val modelManager = mockk<GemmaModelManager>()
        every { modelManager.resolveModelFile() } returns bogusFile
        val provider = GemmaLocalAiProvider(plentifulContext, modelManager)

        assertThrows(LocalAiException.UnsupportedModel::class.java) {
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

    @Test
    fun hasSufficientMemoryToLoadModel_falseWhenSystemReportsLowMemory() {
        assertFalse(hasSufficientMemoryToLoadModel(availableBytes = Long.MAX_VALUE, lowMemory = true, modelFileBytes = 1))
    }

    @Test
    fun hasSufficientMemoryToLoadModel_falseWhenAvailableBelowModelSizeWithOverhead() {
        val modelBytes = 2_000_000_000L
        assertFalse(hasSufficientMemoryToLoadModel(availableBytes = modelBytes, lowMemory = false, modelFileBytes = modelBytes))
    }

    @Test
    fun hasSufficientMemoryToLoadModel_trueWhenAvailableCoversModelSizeWithOverhead() {
        val modelBytes = 2_000_000_000L
        val ample = (modelBytes * 2)
        assertTrue(hasSufficientMemoryToLoadModel(availableBytes = ample, lowMemory = false, modelFileBytes = modelBytes))
    }

    @Test
    fun isValidTaskBundle_trueForZipSignature() {
        val file = File.createTempFile("model", ".task")
        file.deleteOnExit()
        file.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00))

        assertTrue(isValidTaskBundle(file))
    }

    @Test
    fun isValidTaskBundle_falseForNonZipContent() {
        // e.g. a GGUF file (magic "GGUF") mistakenly installed under the mediapipe engine.
        val file = File.createTempFile("model", ".task")
        file.deleteOnExit()
        file.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))

        assertFalse(isValidTaskBundle(file))
    }

    @Test
    fun isValidTaskBundle_falseForTruncatedFile() {
        val file = File.createTempFile("model", ".task")
        file.deleteOnExit()
        file.writeBytes(byteArrayOf(0x50, 0x4B))

        assertFalse(isValidTaskBundle(file))
    }
}
