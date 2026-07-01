package com.mesha.mobile.domain.ai

import android.content.Context
import com.mesha.mobile.localai.model.InstalledModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class GemmaModelManagerTest {

    private fun model(
        id: String = "gemma-model",
        engine: String = "mediapipe",
        installedAtEpochMs: Long = 0L,
    ) = InstalledModel(
        id = id,
        name = "Test Model",
        provider = "Google",
        version = "1.0",
        engine = engine,
        fileName = "model.task",
        sizeBytes = 1_000L,
        sha256 = "abc",
        installedAtEpochMs = installedAtEpochMs,
    )

    /**
     * A [Context] whose [Context.filesDir]/[Context.getExternalFilesDir] resolve to a real,
     * empty temp directory rather than a relaxed-mock default. `filesDir` is non-null in the
     * Android SDK, so a bare `mockk(relaxed = true)` leaves it null and the sideload fallback's
     * `File(context.filesDir, "models")` NPEs the instant it's touched.
     */
    private fun contextWithNoSideloadedFiles(): Context {
        val emptyDir = File.createTempFile("gemma-model-manager-test", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns emptyDir
        every { context.getExternalFilesDir(any()) } returns null
        return context
    }

    @Test
    fun selectMediaPipeModel_returnsNull_whenNoModelsInstalled() {
        assertNull(selectMediaPipeModel(emptyList()))
    }

    @Test
    fun selectMediaPipeModel_ignoresModelsFromOtherEngines() {
        val litertModel = model(id = "qwen", engine = "litertlm")
        assertNull(selectMediaPipeModel(listOf(litertModel)))
    }

    @Test
    fun selectMediaPipeModel_prefersMostRecentlyInstalled_whenMultipleMatch() {
        val older = model(id = "older", installedAtEpochMs = 100L)
        val newer = model(id = "newer", installedAtEpochMs = 200L)
        assertEquals(newer, selectMediaPipeModel(listOf(older, newer)))
    }

    @Test
    fun resolveModelFile_doesNotPickUpAnotherEnginesTaskFile_evenWhenNoMediaPipeModelInstalled() {
        // A model installed under a *different* engine (e.g. litertlm, or a future llama.cpp
        // entry) whose fileName happens to end in ".task" must never be handed to the Gemma
        // provider just because it's the only installed model.
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(model(engine = "litertlm"))
        val manager = GemmaModelManager(contextWithNoSideloadedFiles(), storageManager)

        assertNull(manager.resolveModelFile())
    }

    @Test
    fun resolveModelFile_delegatesToStorageManager_whenMediaPipeModelInstalled() {
        val installed = model()
        val expectedFile = mockk<File>()
        val storageManager = mockk<ModelStorageManager>()
        every { storageManager.installedModels() } returns listOf(installed)
        every { storageManager.modelFile(installed) } returns expectedFile
        val manager = GemmaModelManager(contextWithNoSideloadedFiles(), storageManager)

        assertEquals(expectedFile, manager.resolveModelFile())
    }
}
