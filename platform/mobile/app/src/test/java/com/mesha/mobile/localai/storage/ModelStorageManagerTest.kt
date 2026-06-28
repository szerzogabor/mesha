package com.mesha.mobile.localai.storage

import android.content.Context
import com.mesha.mobile.localai.model.InstalledModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelStorageManagerTest {

    private lateinit var root: File
    private lateinit var storage: ModelStorageManager

    @Before
    fun setUp() {
        root = createTempDir(prefix = "models-test")
        val context = mockk<Context>()
        every { context.getExternalFilesDir("models") } returns File(root, "models")
        every { context.filesDir } returns root
        storage = ModelStorageManager(context, Json { ignoreUnknownKeys = true })
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun installFakeModel(id: String, version: String = "1.0", fileName: String = "model.task") {
        val dir = storage.modelDir(id)
        File(dir, fileName).writeText("weights-for-$id")
        storage.writeMetadata(
            InstalledModel(
                id = id, name = id, provider = "Google", version = version,
                engine = "mediapipe", fileName = fileName,
                sizeBytes = 17, sha256 = "abc", installedAtEpochMs = 1L,
            ),
        )
    }

    @Test
    fun writeAndReadMetadata_roundTrips() {
        installFakeModel("gemma-3n-e2b", version = "1.0")

        val read = storage.readMetadata("gemma-3n-e2b")
        assertNotNull(read)
        assertEquals("1.0", read!!.version)
        assertTrue(storage.isInstalled("gemma-3n-e2b"))
    }

    @Test
    fun readMetadata_returnsNullWhenModelFileMissing() {
        // Metadata present but the artifact deleted => not a valid install.
        val dir = storage.modelDir("ghost")
        storage.writeMetadata(
            InstalledModel("ghost", "ghost", "p", "1.0", "mediapipe", "model.task", 1, "x", 1L),
        )
        assertFalse(File(dir, "model.task").exists())
        assertNull(storage.readMetadata("ghost"))
        assertFalse(storage.isInstalled("ghost"))
    }

    @Test
    fun installedModels_listsEveryInstalledModel() {
        installFakeModel("gemma-3n-e2b")
        installFakeModel("gemma-3n-e4b")

        val installed = storage.installedModels().map { it.id }
        assertEquals(listOf("gemma-3n-e2b", "gemma-3n-e4b"), installed)
    }

    @Test
    fun delete_removesModelDirectory() {
        installFakeModel("gemma-3n-e2b")
        assertTrue(storage.delete("gemma-3n-e2b"))
        assertFalse(storage.isInstalled("gemma-3n-e2b"))
        assertTrue(storage.installedModels().isEmpty())
    }

    @Test
    fun diskUsage_reflectsStoredBytes() {
        installFakeModel("gemma-3n-e2b")
        assertTrue(storage.diskUsageBytes("gemma-3n-e2b") > 0)
        assertTrue(storage.totalDiskUsageBytes() >= storage.diskUsageBytes("gemma-3n-e2b"))
    }

    @Test
    fun hasSpaceFor_falseWhenRequestExceedsAvailable() {
        // Far more than any temp volume could supply, plus the safety margin.
        assertFalse(storage.hasSpaceFor(Long.MAX_VALUE / 2))
        assertTrue(storage.hasSpaceFor(0))
    }
}
