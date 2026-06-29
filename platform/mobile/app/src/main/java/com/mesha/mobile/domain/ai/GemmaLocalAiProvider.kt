package com.mesha.mobile.domain.ai

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
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
 * Whether there's enough free memory to risk loading a model of [modelFileBytes] on disk.
 *
 * The MediaPipe LLM Inference engine has no Java-level memory guard: if the native layer
 * can't satisfy the allocation while mmap'ing the model and reserving its KV cache, it
 * aborts the whole process (a signal, not a catchable exception) — no try/catch in
 * [GemmaLocalAiProvider] can intercept that. Checking available memory first and refusing
 * to attempt the load is the only way to turn that hard crash into a friendly error.
 * [MODEL_MEMORY_OVERHEAD_FACTOR] accounts for the runtime buffers (KV cache, activations,
 * tokenizer) on top of the raw model weights.
 */
internal fun hasSufficientMemoryToLoadModel(
    availableBytes: Long,
    lowMemory: Boolean,
    modelFileBytes: Long,
): Boolean {
    if (lowMemory) return false
    return availableBytes >= (modelFileBytes * MODEL_MEMORY_OVERHEAD_FACTOR).toLong()
}

private const val MODEL_MEMORY_OVERHEAD_FACTOR = 1.3
private const val BYTES_PER_MB = 1024 * 1024L

/**
 * {@link LocalAiProvider} backed by a local Gemma model running through MediaPipe's
 * LLM Inference API (Google AI Edge). Fully offline — no network, no cloud key.
 *
 * Engine creation is lazy and guarded by a mutex: the model is large, so we build the
 * [LlmInference] session once on first use and reuse it. All inference runs on the IO
 * dispatcher. Raw output is normalized into a valid [IssueDraft] by [IssueDraftParser],
 * satisfying the "validate and repair malformed output" contract of [LocalAiProvider].
 */
@Singleton
class GemmaLocalAiProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: GemmaModelManager,
) : LocalAiProvider {

    override val id: String = "gemma"
    override val displayName: String = "Gemma (on-device)"

    private val engineLock = Mutex()
    @Volatile private var engine: LlmInference? = null

    override suspend fun isAvailable(): Boolean = modelManager.isModelInstalled()

    override suspend fun generateIssueDraft(request: GenerateIssueRequest): IssueDraft {
        val raw = runInference(IssuePromptBuilder.build(request))
        return IssueDraftParser.parse(raw, fallbackTitleSource = request.prompt)
    }

    private suspend fun runInference(prompt: String): String = withContext(Dispatchers.IO) {
        val inference = obtainEngine()
        try {
            inference.generateResponse(prompt).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Catches OutOfMemoryError too: loading/running the model is the most
            // memory-intensive step in the app, and an uncaught Error here would
            // otherwise crash the whole process instead of surfacing a message.
            Log.e(TAG, "Gemma inference failed", e)
            throw LocalAiException.InferenceFailed("On-device inference failed: ${e.message}", e)
        }
    }

    private suspend fun obtainEngine(): LlmInference {
        engine?.let { return it }
        return engineLock.withLock {
            engine?.let { return it }
            val modelFile = modelManager.resolveModelFile()
                ?: throw LocalAiException.ModelNotAvailable(
                    "Gemma model not found. Install it from the AI Edge Gallery or Settings."
                )
            checkMemoryAvailable(modelFile)
            try {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(TOP_K)
                    .build()
                LlmInference.createFromOptions(context, options).also { engine = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Same OutOfMemoryError concern as runInference(): the model load itself
                // is the likeliest place to exhaust memory on constrained devices.
                throw LocalAiException.InferenceFailed("Failed to initialize Gemma engine: ${e.message}", e)
            }
        }
    }

    private fun checkMemoryAvailable(modelFile: File) {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val modelBytes = modelFile.length()
        if (!hasSufficientMemoryToLoadModel(info.availMem, info.lowMemory, modelBytes)) {
            val neededMb = (modelBytes * MODEL_MEMORY_OVERHEAD_FACTOR / BYTES_PER_MB).toLong()
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
        const val TAG = "GemmaProvider"
        const val MAX_TOKENS = 1024
        const val TOP_K = 40
    }
}
