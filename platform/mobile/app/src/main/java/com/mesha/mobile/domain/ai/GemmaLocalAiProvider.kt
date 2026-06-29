package com.mesha.mobile.domain.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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
            try {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(TOP_K)
                    .build()
                LlmInference.createFromOptions(context, options).also { engine = it }
            } catch (e: Throwable) {
                // Same OutOfMemoryError concern as runInference(): the model load itself
                // is the likeliest place to exhaust memory on constrained devices.
                throw LocalAiException.InferenceFailed("Failed to initialize Gemma engine: ${e.message}", e)
            }
        }
    }

    /** Releases native resources. Call when the app no longer needs inference. */
    fun close() {
        engine?.close()
        engine = null
    }

    private companion object {
        const val TAG = "GemmaProvider"
        const val MAX_TOKENS = 1024
        const val TOP_K = 40
    }
}
