package com.mesha.mobile.domain.ai

import com.mesha.mobile.localai.storage.ModelStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses which AI provider the ticket generator and chat use: an on-device local model
 * (via [LocalAiProviderRouter]) or ChatGPT (via [RemoteOpenAiProvider], backed by the user's
 * web-configured OpenAI credential). Exposes the available [options] and the currently
 * [selected] choice, remembers the last-used choice across restarts, and hands the right
 * [LocalAiProvider] to callers via [active].
 *
 * The local option is a single "on-device" entry — the router already resolves which installed
 * model to run — kept distinct from ChatGPT so the user picks where generation happens.
 */
@Singleton
class AiProviderCoordinator @Inject constructor(
    private val localRouter: LocalAiProviderRouter,
    private val modelStorageManager: ModelStorageManager,
    private val prefs: AiProviderPreferences,
) {
    // DIAGNOSTIC: RemoteOpenAiProvider temporarily unwired to surface the real KSP2-masked error.
    private val remote: RemoteOpenAiProvider? = null

    private val _options = MutableStateFlow<List<AiProviderChoice>>(emptyList())
    val options: StateFlow<List<AiProviderChoice>> = _options.asStateFlow()

    private val _selected = MutableStateFlow<AiProviderChoice?>(null)
    val selected: StateFlow<AiProviderChoice?> = _selected.asStateFlow()

    /**
     * Recompute the available providers — installed local model(s) + ChatGPT when configured —
     * and reconcile the selection: keep the persisted last-used choice if it's still available,
     * otherwise fall back to the first option (local is listed first), or none.
     */
    suspend fun refresh() {
        // Resolve suspend inputs first, then build the list — keep the suspend call out of
        // any builder/inline lambda.
        val installed = modelStorageManager.installedModels()
        val chatGptAvailable = remote?.isAvailable() ?: false

        val opts = mutableListOf<AiProviderChoice>()
        if (installed.isNotEmpty()) {
            val label = if (installed.size == 1) installed.first().name else "On-device model"
            opts.add(AiProviderChoice(AiProviderKeys.LOCAL, label, AiProviderKind.LOCAL))
        }
        if (chatGptAvailable) {
            opts.add(AiProviderChoice(AiProviderKeys.OPENAI, "ChatGPT", AiProviderKind.OPENAI))
        }

        _options.value = opts
        _selected.value = opts.firstOrNull { it.key == prefs.selectedKey } ?: opts.firstOrNull()
    }

    /** Set the active provider by key and remember it. No-op if the key isn't currently available. */
    fun select(key: String) {
        val choice = _options.value.firstOrNull { it.key == key } ?: return
        prefs.selectedKey = choice.key
        _selected.value = choice
    }

    /** The provider to run for the current selection (defaults to on-device). */
    fun active(): LocalAiProvider =
        if (_selected.value?.kind == AiProviderKind.OPENAI && remote != null) remote else localRouter
}
