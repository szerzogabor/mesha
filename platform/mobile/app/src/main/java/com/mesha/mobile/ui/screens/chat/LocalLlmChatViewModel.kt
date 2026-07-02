package com.mesha.mobile.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mesha.mobile.domain.ai.LocalAiException
import com.mesha.mobile.domain.ai.LocalAiProvider
import com.mesha.mobile.domain.ai.LocalChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalLlmChatUiState(
    val messages: List<LocalChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val modelAvailable: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LocalLlmChatViewModel @Inject constructor(
    private val localAi: LocalAiProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalLlmChatUiState())
    val state: StateFlow<LocalLlmChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(modelAvailable = localAi.isAvailable()) }
        }
    }

    fun onInputChange(text: String) = _state.update { it.copy(inputText = text, error = null) }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isGenerating) return

        val userMessage = LocalChatMessage(LocalChatMessage.Role.USER, text)
        val history = _state.value.messages + userMessage

        _state.update {
            it.copy(
                messages = history,
                inputText = "",
                isGenerating = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val response = localAi.generateChatResponse(history)
                val assistantMessage = LocalChatMessage(LocalChatMessage.Role.ASSISTANT, response)
                _state.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        isGenerating = false,
                    )
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
