package com.mesha.mobile.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.domain.ai.AiProviderChoice
import com.mesha.mobile.domain.ai.AiProviderCoordinator
import com.mesha.mobile.domain.ai.LocalAiException
import com.mesha.mobile.domain.ai.LocalChatMessage
import com.mesha.mobile.domain.ai.agent.AgentStep
import com.mesha.mobile.domain.ai.agent.TicketAgent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One item in the chat transcript: a person's message, the agent's reply, or a tool step. */
sealed interface ChatEntry {
    data class User(val text: String) : ChatEntry
    data class Assistant(val text: String) : ChatEntry

    /** A tool the agent invoked. [running] flips to false and [detail] fills in once it returns. */
    data class Tool(val title: String, val detail: String?, val running: Boolean) : ChatEntry
}

data class LocalLlmChatUiState(
    val entries: List<ChatEntry> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val modelAvailable: Boolean = true,
    val providerOptions: List<AiProviderChoice> = emptyList(),
    val selectedProviderKey: String? = null,
    val error: String? = null,
)

/**
 * Drives the on-device AI chat. Instead of a plain single-shot completion, messages are handled
 * by [TicketAgent], which can read and modify the user's Mesha tickets by calling tools while it
 * reasons. The agent's tool activity is surfaced as [ChatEntry.Tool] rows so the user can see
 * what it's doing; its final answer becomes a [ChatEntry.Assistant] row.
 *
 * [localAi] is retained only to report model availability for the banner/enablement — all
 * generation goes through [agent].
 */
@HiltViewModel
class LocalLlmChatViewModel @Inject constructor(
    private val agent: TicketAgent,
    private val coordinator: AiProviderCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalLlmChatUiState())
    val state: StateFlow<LocalLlmChatUiState> = _state.asStateFlow()

    /** Conversation as the agent sees it — user messages and the agent's final replies only. */
    private val conversation = mutableListOf<LocalChatMessage>()

    init {
        viewModelScope.launch { coordinator.refresh() }
        viewModelScope.launch {
            combine(coordinator.options, coordinator.selected) { opts, sel -> opts to sel }
                .collect { (opts, sel) ->
                    _state.update {
                        it.copy(
                            providerOptions = opts,
                            selectedProviderKey = sel?.key,
                            modelAvailable = opts.isNotEmpty(),
                        )
                    }
                }
        }
    }

    fun onInputChange(text: String) = _state.update { it.copy(inputText = text, error = null) }

    fun onSelectProvider(key: String) = coordinator.select(key)

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isGenerating) return

        conversation.add(LocalChatMessage(LocalChatMessage.Role.USER, text))
        _state.update {
            it.copy(
                entries = it.entries + ChatEntry.User(text),
                inputText = "",
                isGenerating = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val reply = agent.run(conversation.toList()) { step -> onAgentStep(step) }
                conversation.add(LocalChatMessage(LocalChatMessage.Role.ASSISTANT, reply))
                _state.update {
                    it.copy(entries = it.entries + ChatEntry.Assistant(reply), isGenerating = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocalAiException) {
                _state.update { it.copy(isGenerating = false, error = friendlyMessage(e)) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isGenerating = false, error = "Chat failed: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    private fun onAgentStep(step: AgentStep) {
        when (step) {
            is AgentStep.ToolInvocation -> _state.update {
                it.copy(entries = it.entries + ChatEntry.Tool(step.summary, detail = null, running = true))
            }
            is AgentStep.ToolResult -> _state.update { state ->
                // Complete the most recent still-running tool row with its observation.
                val entries = state.entries.toMutableList()
                val idx = entries.indexOfLast { it is ChatEntry.Tool && it.running }
                if (idx >= 0) {
                    val row = entries[idx] as ChatEntry.Tool
                    entries[idx] = row.copy(detail = step.observation, running = false)
                }
                state.copy(entries = entries)
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun friendlyMessage(e: LocalAiException): String = when (e) {
        is LocalAiException.ModelNotAvailable ->
            e.message ?: "No AI provider available. Install an on-device model in Settings, or connect ChatGPT on the web."
        is LocalAiException.InvalidOutput ->
            "Unexpected model response. Try sending another message."
        is LocalAiException.InferenceFailed ->
            e.message ?: "Inference failed."
        is LocalAiException.UnsupportedModel ->
            "This model isn't supported. Try a different one in Settings."
    }
}
