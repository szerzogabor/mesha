package com.mesha.mobile.localai.download

import com.mesha.mobile.localai.catalog.ModelCatalogRepository
import com.mesha.mobile.localai.model.DownloadError
import com.mesha.mobile.localai.model.DownloadState
import com.mesha.mobile.localai.model.LocalAiModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import com.mesha.mobile.localai.util.Sha256
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

class ModelDownloadManagerTest {

    private lateinit var server: TinyRangeServer
    private lateinit var modelDir: File
    private lateinit var storage: ModelStorageManager
    private lateinit var catalogRepository: ModelCatalogRepository
    private lateinit var manager: ModelDownloadManager

    private val content = ByteArray(64 * 1024) { (it % 256).toByte() }
    private val contentSha = run {
        val tmp = File.createTempFile("expected", ".bin").apply { writeBytes(content) }
        Sha256.ofFile(tmp).also { tmp.delete() }
    }

    @Before
    fun setUp() {
        server = TinyRangeServer(content).also { it.start() }

        modelDir = createTempDirectory("dl-test").toFile()
        storage = mockk(relaxed = true)
        every { storage.modelDir(any()) } returns modelDir
        every { storage.hasSpaceFor(any()) } returns true

        catalogRepository = mockk()

        manager = ModelDownloadManager(OkHttpClient(), storage, catalogRepository)
    }

    @After
    fun tearDown() {
        server.stop()
        modelDir.deleteRecursively()
    }

    private fun model(sha: String, resolveUrl: String? = null) = LocalAiModel(
        id = "gemma-3n-e2b", name = "Gemma 3n E2B", provider = "Google", source = "huggingface",
        version = "1.0", engine = "mediapipe", fileName = "model.task",
        sizeBytes = content.size.toLong(), sha256 = sha,
        downloadUrl = "http://127.0.0.1:${server.port}/model.task",
        resolveUrl = resolveUrl,
    )

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
        assertTrue(server.rangeHeadersSeen.any { it == "bytes=$half-" })
    }

    @Test
    fun download_resolvesAndUsesCdnUrlFirst_whenResolveUrlIsSet() = runTest {
        val cdnServer = TinyRangeServer(content).also { it.start() }
        try {
            coEvery { catalogRepository.resolveDownloadUrl("gemma-3n-e2b") } returns
                Result.success("http://127.0.0.1:${cdnServer.port}/model.task")

            val states = manager.download(model(contentSha, resolveUrl = "https://backend/resolve")).toList()

            assertTrue(states.last() is DownloadState.Completed)
            assertTrue(cdnServer.requestCount.get() > 0)
            assertEquals(0, server.requestCount.get())
        } finally {
            cdnServer.stop()
        }
    }

    @Test
    fun download_fallsBackToProxyUrl_whenResolveFails() = runTest {
        coEvery { catalogRepository.resolveDownloadUrl("gemma-3n-e2b") } returns
            Result.failure(RuntimeException("resolve unavailable"))

        val states = manager.download(model(contentSha, resolveUrl = "https://backend/resolve")).toList()

        assertTrue(states.last() is DownloadState.Completed)
        assertTrue(server.requestCount.get() > 0)
    }

    @Test
    fun download_doesNotReResolve_whenInitialResolveFailsAndProxyAlsoReturns401() = runTest {
        // Initial resolve already failed, so we're on the proxy URL from the very first attempt.
        // A 401 from the proxy itself must not trigger a redundant re-resolve attempt.
        coEvery { catalogRepository.resolveDownloadUrl("gemma-3n-e2b") } returns
            Result.failure(RuntimeException("resolve unavailable"))
        server.unauthorizedRequestsRemaining.set(1)

        val states = manager.download(model(contentSha, resolveUrl = "https://backend/resolve")).toList()

        assertTrue(states.last() is DownloadState.Failed)
        coVerify(exactly = 1) { catalogRepository.resolveDownloadUrl("gemma-3n-e2b") }
    }

    @Test
    fun download_reResolvesOnceOn401AndResumesFromExistingPartFile() = runTest {
        val cdnServer = TinyRangeServer(content).also { it.start() }
        try {
            cdnServer.unauthorizedRequestsRemaining.set(1)
            coEvery { catalogRepository.resolveDownloadUrl("gemma-3n-e2b") } returns
                Result.success("http://127.0.0.1:${cdnServer.port}/model.task")

            val half = content.size / 2
            File(modelDir, "model.task.part").writeBytes(content.copyOfRange(0, half))

            val states = manager.download(model(contentSha, resolveUrl = "https://backend/resolve")).toList()

            assertTrue(states.last() is DownloadState.Completed)
            assertArrayEquals(content, File(modelDir, "model.task").readBytes())
            assertEquals(2, cdnServer.requestCount.get())
            assertEquals("bytes=$half-", cdnServer.rangeHeadersSeen.last())
            coVerify(exactly = 2) { catalogRepository.resolveDownloadUrl("gemma-3n-e2b") }
            assertEquals(0, server.requestCount.get())
        } finally {
            cdnServer.stop()
        }
    }

    @Test
    fun download_fallsBackToProxyPermanently_afterSecondConsecutive401() = runTest {
        val cdnServer = TinyRangeServer(content).also { it.start() }
        try {
            cdnServer.unauthorizedRequestsRemaining.set(2)
            coEvery { catalogRepository.resolveDownloadUrl("gemma-3n-e2b") } returns
                Result.success("http://127.0.0.1:${cdnServer.port}/model.task")

            val states = manager.download(model(contentSha, resolveUrl = "https://backend/resolve")).toList()

            assertTrue(states.last() is DownloadState.Completed)
            assertEquals(2, cdnServer.requestCount.get())
            assertTrue(server.requestCount.get() > 0)
            coVerify(exactly = 2) { catalogRepository.resolveDownloadUrl("gemma-3n-e2b") }
        } finally {
            cdnServer.stop()
        }
    }

    @Test
    fun download_doesNotCallResolve_whenResolveUrlIsNull() = runTest {
        val states = manager.download(model(contentSha, resolveUrl = null)).toList()

        assertTrue(states.last() is DownloadState.Completed)
        coVerify(exactly = 0) { catalogRepository.resolveDownloadUrl(any()) }
    }

    /**
     * Minimal HTTP/1.1 server backed by a raw [ServerSocket] — Android's unit-test toolchain
     * excludes `com.sun.net.httpserver`, so we hand-roll just enough to serve bytes with
     * `Range` support for the resume test.
     */
    private class TinyRangeServer(private val content: ByteArray) {
        private val socket = ServerSocket(0)
        val port: Int get() = socket.localPort
        val rangeHeadersSeen = CopyOnWriteArrayList<String?>()
        val requestCount = AtomicInteger(0)

        /** Number of upcoming requests to answer with a bare 401, simulating an expired signed URL. */
        val unauthorizedRequestsRemaining = AtomicInteger(0)

        @Volatile private var running = true
        private val thread = Thread {
            while (running) {
                val client = try { socket.accept() } catch (e: Exception) { break }
                runCatching { handle(client) }
            }
        }.apply { isDaemon = true }

        fun start() = thread.start()

        fun stop() {
            running = false
            runCatching { socket.close() }
        }

        private fun handle(client: Socket) {
            client.use {
                val reader = it.getInputStream().bufferedReader()
                reader.readLine() ?: return // request line
                var range: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        range = line.substringAfter(":").trim()
                    }
                }
                rangeHeadersSeen.add(range)
                requestCount.incrementAndGet()

                val out = it.getOutputStream()
                if (unauthorizedRequestsRemaining.get() > 0) {
                    unauthorizedRequestsRemaining.decrementAndGet()
                    out.write(
                        ("HTTP/1.1 401 Unauthorized\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(),
                    )
                    out.flush()
                    return
                }
                if (range != null) {
                    val start = range.removePrefix("bytes=").substringBefore("-").toInt()
                    val slice = content.copyOfRange(start, content.size)
                    out.write(
                        ("HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Length: ${slice.size}\r\n" +
                            "Content-Range: bytes $start-${content.size - 1}/${content.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(),
                    )
                    out.write(slice)
                } else {
                    out.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Length: ${content.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(),
                    )
                    out.write(content)
                }
                out.flush()
            }
        }
    }
}
