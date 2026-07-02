package com.mesha.mobile.domain.ai

/** A single turn in a free-form conversation with the local LLM. */
data class LocalChatMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { USER, ASSISTANT }
}
