package com.mesha.mobile.domain.ai

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether there's enough free memory to risk loading a LiteRT-LM model of [modelFileBytes] on disk.
 *
 * Same hard-crash risk as MediaPipe's engine (see [hasSufficientMemoryToLoadModel] in
 * GemmaLocalAiProvider.kt): LiteRT-LM is also a native library that mmaps a multi-GB file and
 * reserves runtime buffers, with no Java-level guard — an under-provisioned load aborts the
 * whole process rather than throwing. The overhead factor starts at the same conservative value
 * as the MediaPipe path pending a real peak-RSS measurement on-device.
 */
internal fun hasSufficientMemoryToLoadLiteRtLmModel(
    availableBytes: Long,
    lowMemory: Boolean,
    modelFileBytes: Long,
): Boolean {
    if (lowMemory) return false
    return availableBytes >= (modelFileBytes * LITERTLM_MEMORY_OVERHEAD_FACTOR).toLong()
}

private const val LITERTLM_MEMORY_OVERHEAD_FACTOR = 1.3
private const val BYTES_PER_MB = 1024 * 1024L

/**
 * {@link LocalAiProvider} backed by a local model running through Google's LiteRT-LM runtime
 * (`google-ai-edge/LiteRT-LM`) — a separate engine and `.litertlm` file format from MediaPipe's
 * `tasks-genai` (see [GemmaLocalAiProvider]). Added so models that don't fit MediaPipe's memory
 * footprint on a given device can still run on-device. Fully offline — no network, no cloud key.
 *
 * Mirrors [GemmaLocalAiProvider]'s lifecycle: the [Engine] is large and expensive to create, so
 * it's built lazily under a mutex and reused; both engine creation and inference run on the IO
 * dispatcher; [CancellationException] is rethrown before the broad [Throwable] catch so coroutine
 * cancellation isn't swallowed. CPU backend only for this first cut — no GPU/NPU support yet.
 */
@Singleton
class LiteRtLmLocalAiProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelLocator: LiteRtLmModelLocator,
) : LocalAiProvider {

    override val id: String = "litertlm"
    override val displayName: String = "Gemma (LiteRT-LM, on-device)"

    private val engineLock = Mutex()
    @Volatile private var engine: Engine? = null

    override suspend fun isAvailable(): Boolean = modelLocator.isModelInstalled()

    override suspend fun generateIssueDraft(request: GenerateIssueRequest): IssueDraft {
        val raw = runInference(IssuePromptBuilder.build(request))
        return IssueDraftParser.parse(raw, fallbackTitleSource = request.prompt)
    }

    private suspend fun runInference(prompt: String): String = withContext(Dispatchers.IO) {
        val inference = obtainEngine()
        try {
            inference.createConversation().use { conversation ->
                conversation.sendMessage(prompt).contents.toString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Catches OutOfMemoryError too: loading/running the model is the most
            // memory-intensive step in the app, and an uncaught Error here would
            // otherwise crash the whole process instead of surfacing a message.
            Log.e(TAG, "LiteRT-LM inference failed", e)
            throw LocalAiException.InferenceFailed("On-device inference failed: ${e.message}", e)
        }
    }

    private suspend fun obtainEngine(): Engine {
        engine?.let { return it }
        return engineLock.withLock {
            engine?.let { return it }
            val modelFile = modelLocator.resolveModelFile()
                ?: throw LocalAiException.ModelNotAvailable(
                    "LiteRT-LM model not found. Install it from Settings."
                )
            checkMemoryAvailable(modelFile)
            try {
                val config = EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU())
                val newEngine = Engine(config)
                try {
                    newEngine.initialize()
                } catch (e: Throwable) {
                    // initialize() failed after the native engine was allocated — close it
                    // before rethrowing so we don't leak native memory on a failed load.
                    newEngine.close()
                    throw e
                }
                newEngine.also { engine = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Same OutOfMemoryError concern as runInference(): the model load itself
                // is the likeliest place to exhaust memory on constrained devices.
                throw LocalAiException.InferenceFailed("Failed to initialize LiteRT-LM engine: ${e.message}", e)
            }
        }
    }

    private fun checkMemoryAvailable(modelFile: File) {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val modelBytes = modelFile.length()
        if (!hasSufficientMemoryToLoadLiteRtLmModel(info.availMem, info.lowMemory, modelBytes)) {
            val neededMb = (modelBytes * LITERTLM_MEMORY_OVERHEAD_FACTOR / BYTES_PER_MB).toLong()
            val availableMb = info.availMem / BYTES_PER_MB
            throw LocalAiException.InferenceFailed(
                "Not enough free memory to load the on-device model (~$neededMb MB needed, " +
                    "$availableMb MB available). Close other apps and try again."
            )
        }
    }

    /** Releases native resources. Call when the app no longer needs inference. */
    override fun close() {
        engine?.close()
        engine = null
    }

    private companion object {
        const val TAG = "LiteRtLmProvider"
    }
}
