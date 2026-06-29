package com.mesha.mobile.domain.ai

import com.mesha.mobile.localai.model.InstalledModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiteRtLmModelLocatorTest {

    private fun model(
        id: String = "litert-model",
        engine: String = "litertlm",
        installedAtEpochMs: Long = 0L,
    ) = InstalledModel(
        id = id,
        name = "Test Model",
        provider = "Google",
        version = "1.0",
        engine = engine,
        fileName = "model.litertlm",
        sizeBytes = 1_000L,
        sha256 = "abc",
        installedAtEpochMs = installedAtEpochMs,
    )

    @Test
    fun selectLiteRtLmModel_returnsNull_whenNoModelsInstalled() {
        assertNull(selectLiteRtLmModel(emptyList()))
    }

    @Test
    fun selectLiteRtLmModel_ignoresModelsFromOtherEngines() {
        val mediapipeModel = model(id = "gemma", engine = "mediapipe")
        assertNull(selectLiteRtLmModel(listOf(mediapipeModel)))
    }

    @Test
    fun selectLiteRtLmModel_returnsTheOnlyMatchingModel() {
        val litertModel = model()
        assertEquals(litertModel, selectLiteRtLmModel(listOf(litertModel)))
    }

    @Test
    fun selectLiteRtLmModel_prefersMostRecentlyInstalled_whenMultipleMatch() {
        val older = model(id = "older", installedAtEpochMs = 100L)
        val newer = model(id = "newer", installedAtEpochMs = 200L)
        assertEquals(newer, selectLiteRtLmModel(listOf(older, newer)))
    }

    @Test
    fun resolveModelFile_returnsNull_whenNoLiteRtLmModelInstalled() {
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(model(engine = "mediapipe"))
        val locator = LiteRtLmModelLocator(storageManager)

        assertNull(locator.resolveModelFile())
        assertFalse(locator.isModelInstalled())
    }

    @Test
    fun resolveModelFile_delegatesToStorageManager_whenModelInstalled() {
        val installed = model()
        val expectedFile = mockk<File>()
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(installed)
        every { storageManager.modelFile(installed) } returns expectedFile
        val locator = LiteRtLmModelLocator(storageManager)

        assertEquals(expectedFile, locator.resolveModelFile())
        assertTrue(locator.isModelInstalled())
    }
}
