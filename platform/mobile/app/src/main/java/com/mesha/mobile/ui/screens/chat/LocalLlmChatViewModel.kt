package com.mesha.mobile.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.data.local.chat.ChatRepository
import com.mesha.mobile.domain.ai.LocalAiException
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.ai.LocalChatMessage
import com.mesha.mobile.domain.ai.agent.AgentStep
import com.mesha.mobile.domain.ai.agent.TicketAgent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: String? = null,
)

/**
 * Drives the on-device AI chat. Instead of a plain single-shot completion, messages are handled
 * by [TicketAgent], which can read and modify the user's Mesha tickets by calling tools while it
 * reasons. The agent's tool activity is surfaced as [ChatEntry.Tool] rows so the user can see
 * what it's doing; its final answer becomes a [ChatEntry.Assistant] row.
 *
 * The conversation is persisted via [ChatRepository] so it survives app restarts. Users can
 * wipe the history at any time with [clearSession].
 */
@HiltViewModel
class LocalLlmChatViewModel @Inject constructor(
    private val agent: TicketAgent,
    private val localAi: LocalAiProvider,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalLlmChatUiState())
    val state: StateFlow<LocalLlmChatUiState> = _state.asStateFlow()

    /** Conversation as the agent sees it — user messages and the agent's final replies only. */
    private val conversation = mutableListOf<LocalChatMessage>()

    init {
        viewModelScope.launch {
            _state.update { it.copy(modelAvailable = localAi.isAvailable()) }
            loadPersistedSession()
        }
    }

    private suspend fun loadPersistedSession() {
        val persisted = chatRepository.loadMessages()
        conversation.addAll(persisted)
        val entries = persisted.map { msg ->
            when (msg.role) {
                LocalChatMessage.Role.USER -> ChatEntry.User(msg.content)
                LocalChatMessage.Role.ASSISTANT -> ChatEntry.Assistant(msg.content)
            }
        }
        _state.update { it.copy(entries = entries) }
    }

    fun onInputChange(text: String) = _state.update { it.copy(inputText = text, error = null) }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isGenerating) return

        val userMessage = LocalChatMessage(LocalChatMessage.Role.USER, text)
        conversation.add(userMessage)
        _state.update {
            it.copy(
                entries = it.entries + ChatEntry.User(text),
                inputText = "",
                isGenerating = true,
                error = null,
            )
        }

        viewModelScope.launch {
            chatRepository.saveMessage(userMessage)
            try {
                val reply = agent.run(conversation.toList()) { step -> onAgentStep(step) }
                val assistantMessage = LocalChatMessage(LocalChatMessage.Role.ASSISTANT, reply)
                conversation.add(assistantMessage)
                chatRepository.saveMessage(assistantMessage)
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

    fun clearSession() {
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            chatRepository.clearSession()
            conversation.clear()
            _state.update { it.copy(entries = emptyList(), error = null) }
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
            "On-device model isn't installed. Add it in Settings to chat."
        is LocalAiException.InvalidOutput ->
            "Unexpected model response. Try sending another message."
        is LocalAiException.InferenceFailed ->
            e.message ?: "On-device inference failed."
        is LocalAiException.UnsupportedModel ->
            "This model isn't supported. Try a different one in Settings."
    }
}
