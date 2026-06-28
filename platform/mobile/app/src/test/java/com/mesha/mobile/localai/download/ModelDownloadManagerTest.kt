package com.mesha.mobile.localai.download

import com.mesha.mobile.localai.model.DownloadError
import com.mesha.mobile.localai.model.DownloadState
import com.mesha.mobile.localai.model.LocalAiModel
import com.mesha.mobile.localai.storage.ModelStorageManager
import com.mesha.mobile.localai.util.Sha256
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
import kotlin.io.path.createTempDirectory

class ModelDownloadManagerTest {

    private lateinit var server: TinyRangeServer
    private lateinit var modelDir: File
    private lateinit var storage: ModelStorageManager
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

        manager = ModelDownloadManager(OkHttpClient(), storage)
    }

    @After
    fun tearDown() {
        server.stop()
        modelDir.deleteRecursively()
    }

    private fun model(sha: String) = LocalAiModel(
        id = "gemma-3n-e2b", name = "Gemma 3n E2B", provider = "Google", source = "huggingface",
        version = "1.0", engine = "mediapipe", fileName = "model.task",
        sizeBytes = content.size.toLong(), sha256 = sha,
        downloadUrl = "http://127.0.0.1:${server.port}/model.task",
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

    /**
     * Minimal HTTP/1.1 server backed by a raw [ServerSocket] — Android's unit-test toolchain
     * excludes `com.sun.net.httpserver`, so we hand-roll just enough to serve bytes with
     * `Range` support for the resume test.
     */
    private class TinyRangeServer(private val content: ByteArray) {
        private val socket = ServerSocket(0)
        val port: Int get() = socket.localPort
        val rangeHeadersSeen = CopyOnWriteArrayList<String?>()

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

                val out = it.getOutputStream()
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
