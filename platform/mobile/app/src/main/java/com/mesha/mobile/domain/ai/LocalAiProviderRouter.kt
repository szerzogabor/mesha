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

    override suspend fun isAvailable(): Boolean = resolveProvider()?.isAvailable() == true

    override suspend fun generateIssueDraft(request: GenerateIssueRequest): IssueDraft {
        val provider = resolveProvider()
            ?: throw LocalAiException.ModelNotAvailable(
                "No on-device model is installed. Install one from Settings."
            )
        return provider.generateIssueDraft(request)
    }

    private fun resolveProvider(): LocalAiProvider? =
        modelStorageManager.installedModels()
            .asSequence()
            .mapNotNull { providers[it.engine] }
            .firstOrNull()
}
