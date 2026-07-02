package com.mesha.mobile.domain.ai

/**
 * Abstraction over an on-device language model that can turn a natural-language prompt
 * into a structured {@link IssueDraft}.
 *
 * The application depends only on this interface — never on Gemma / MediaPipe details —
 * so the inference backend can be swapped (a different local model, a remote fallback,
 * or a fake for tests) without touching feature code.
 */
interface LocalAiProvider : AutoCloseable {

    /** Stable identifier used in logs and settings (e.g. "gemma"). */
    val id: String

    /** Human-readable name shown in Settings. */
    val displayName: String

    /**
     * Whether the provider is ready to run inference right now. For an on-device model
     * this typically means the model weights have been downloaded and the engine
     * initialized. Cheap to call; safe to poll from the UI.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Generate a structured issue draft from a free-form description.
     *
     * Implementations MUST return a valid [IssueDraft] or throw [LocalAiException];
     * they must never return malformed/partial data. Provider implementations are
     * responsible for validating and repairing the raw model output.
     *
     * @throws LocalAiException when the model is unavailable or output cannot be repaired.
     */
    suspend fun generateIssueDraft(request: GenerateIssueRequest): IssueDraft

    /**
     * Generate a free-form chat reply given the full conversation [history].
     *
     * The last entry in [history] must be a USER message. Implementations format the
     * history into a prompt and return the raw model response as a trimmed string.
     *
     * @throws LocalAiException when the model is unavailable or inference fails.
     */
    suspend fun generateChatResponse(history: List<LocalChatMessage>): String

    /** Releases native resources, if any. Default no-op for providers with nothing to free. */
    override fun close() {}
}

/** Failure modes surfaced to the UI as user-friendly messages. */
sealed class LocalAiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The on-device model is not installed / not yet downloaded. */
    class ModelNotAvailable(message: String = "On-device model is not available") :
        LocalAiException(message)

    /** Inference ran but produced output that could not be parsed or repaired. */
    class InvalidOutput(message: String, cause: Throwable? = null) :
        LocalAiException(message, cause)

    /** Inference engine failed (OOM, native crash, etc.). */
    class InferenceFailed(message: String, cause: Throwable? = null) :
        LocalAiException(message, cause)

    /** The installed model file isn't a valid bundle for its engine and can't be loaded. */
    class UnsupportedModel(message: String) : LocalAiException(message)
}
