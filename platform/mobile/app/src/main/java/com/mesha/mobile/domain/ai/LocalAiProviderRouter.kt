package com.mesha.mobile.domain.ai

import com.mesha.mobile.localai.storage.ModelStorageManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches [LocalAiProvider] calls to whichever inference engine's model is actually
 * installed. Mesha supports more than one on-device engine (MediaPipe, LiteRT-LM) because a
 * single engine's models don't fit every device. In practice at most one engine's model is
 * installed at a time (device storage constraints), so this is a lookup keyed by the installed
 * model's `engine` field, not a ranking/priority system — feature code depends only on this
 * unqualified [LocalAiProvider] binding and never needs to know which engine is active.
 */
@Singleton
class LocalAiProviderRouter @Inject constructor(
    private val providers: Map<String, @JvmSuppressWildcards LocalAiProvider>,
    private val modelStorageManager: ModelStorageManager,
) : LocalAiProvider {

    override val id: String = "router"
    override val displayName: String = "On-device AI"

    @Volatile private var activeEngineKey: String? = null

    override suspend fun isAvailable(): Boolean = resolveProvider()?.isAvailable() == true

    override suspend fun generateIssueDraft(request: GenerateIssueRequest): IssueDraft {
        val provider = requireProvider()
        return provider.generateIssueDraft(request)
    }

    override suspend fun generateChatResponse(history: List<LocalChatMessage>): String {
        val provider = requireProvider()
        return provider.generateChatResponse(history)
    }

    private fun requireProvider(): LocalAiProvider =
        resolveProvider() ?: throw LocalAiException.ModelNotAvailable(
            "No on-device model is installed. Install one from Settings."
        )

    /**
     * Resolves the provider for the currently installed model, closing the previously active
     * provider first if the installed engine changed since the last call — otherwise switching
     * models without an app restart would leak the old provider's native resources forever.
     */
    private fun resolveProvider(): LocalAiProvider? {
        val installedEngine = modelStorageManager.installedModels()
            .asSequence()
            .map { it.engine }
            .firstOrNull { providers.containsKey(it) }
        if (installedEngine != activeEngineKey) {
            activeEngineKey?.let { providers[it]?.close() }
            activeEngineKey = installedEngine
        }
        return installedEngine?.let { providers[it] }
    }
}
