package com.mesha.mobile.localai.download

import com.mesha.mobile.localai.model.DownloadError
import com.mesha.mobile.localai.model.DownloadState
import com.mesha.mobile.localai.model.LocalAiModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import com.mesha.mobile.localai.util.Sha256
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class ModelDownloadManagerTest {

    private lateinit var server: HttpServer
    private lateinit var modelDir: File
    private lateinit var storage: ModelStorageManager
    private lateinit var manager: ModelDownloadManager

    private val content = ByteArray(64 * 1024) { (it % 256).toByte() }
    private val contentSha = run {
        val tmp = File.createTempFile("expected", ".bin").apply { writeBytes(content) }
        Sha256.ofFile(tmp).also { tmp.delete() }
    }
    private val rangeHeaders = CopyOnWriteArrayList<String?>()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/model.task") { exchange -> serve(exchange, rangeSupported = true) }
        server.start()

        modelDir = createTempDir(prefix = "dl-test")
        storage = mockk(relaxed = true)
        every { storage.modelDir(any()) } returns modelDir
        every { storage.hasSpaceFor(any()) } returns true

        manager = ModelDownloadManager(OkHttpClient(), storage)
    }

    @After
    fun tearDown() {
        server.stop(0)
        modelDir.deleteRecursively()
    }

    private fun serve(exchange: HttpExchange, rangeSupported: Boolean) {
        val range = exchange.requestHeaders.getFirst("Range")
        rangeHeaders.add(range)
        if (rangeSupported && range != null) {
            val start = range.removePrefix("bytes=").substringBefore("-").toInt()
            val slice = content.copyOfRange(start, content.size)
            exchange.responseHeaders.add("Content-Range", "bytes $start-${content.size - 1}/${content.size}")
            exchange.sendResponseHeaders(206, slice.size.toLong())
            exchange.responseBody.use { it.write(slice) }
        } else {
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.use { it.write(content) }
        }
    }

    private fun model(sha: String) = LocalAiModel(
        id = "gemma-3n-e2b", name = "Gemma 3n E2B", provider = "Google", source = "huggingface",
        version = "1.0", engine = "mediapipe", fileName = "model.task",
        sizeBytes = content.size.toLong(), sha256 = sha,
        downloadUrl = url(),
    )

    private fun url() = "http://127.0.0.1:${server.address.port}/model.task"

    @Test
    fun download_verifiesChecksumAndInstalls() = runTest {
        val states = manager.download(model(contentSha)).toList()

        assertTrue(states.first() is DownloadState.Connecting)
        assertTrue(states.any { it is DownloadState.Downloading })
        assertTrue(states.any { it is DownloadState.Verifying })
        assertTrue(states.last() is DownloadState.Completed)

        val installedFile = File(modelDir, "model.task")
        assertTrue(installedFile.exists())
        assertArrayEquals(content, installedFile.readBytes())
        verify { storage.writeMetadata(any()) }
        // The temporary part file is gone after a successful install.
        assertFalse(File(modelDir, "model.task.part").exists())
    }

    @Test
    fun download_emitsProgressTowardCompletion() = runTest {
        val states = manager.download(model(contentSha)).toList()
        val progress = states.filterIsInstance<DownloadState.Downloading>()
        assertTrue(progress.isNotEmpty())
        assertEquals(content.size.toLong(), progress.last().bytesDownloaded)
        assertEquals(1f, progress.last().fraction)
    }

    @Test
    fun download_failsOnChecksumMismatchAndDeletesPartFile() = runTest {
        val states = manager.download(model("deadbeefmismatch")).toList()

        val failure = states.last()
        assertTrue(failure is DownloadState.Failed)
        assertEquals(DownloadError.CHECKSUM_MISMATCH, (failure as DownloadState.Failed).reason)
        assertFalse(File(modelDir, "model.task.part").exists())
        assertFalse(File(modelDir, "model.task").exists())
        verify(exactly = 0) { storage.writeMetadata(any()) }
    }

    @Test
    fun download_failsWhenInsufficientStorage() = runTest {
        every { storage.hasSpaceFor(any()) } returns false

        val states = manager.download(model(contentSha)).toList()
        val failure = states.last()
        assertTrue(failure is DownloadState.Failed)
        assertEquals(DownloadError.INSUFFICIENT_STORAGE, (failure as DownloadState.Failed).reason)
    }

    @Test
    fun download_resumesFromExistingPartFile() = runTest {
        // Simulate an interrupted download: half the bytes already on disk.
        val half = content.size / 2
        File(modelDir, "model.task.part").writeBytes(content.copyOfRange(0, half))

        val states = manager.download(model(contentSha)).toList()

        assertTrue(states.last() is DownloadState.Completed)
        assertArrayEquals(content, File(modelDir, "model.task").readBytes())
        // The server must have received a Range request to continue from the prefix.
        assertTrue(rangeHeaders.any { it == "bytes=$half-" })
    }
}
