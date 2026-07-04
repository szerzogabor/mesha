package com.mesha.mobile.domain.ai

/** Whether a provider choice runs on-device or via the remote (ChatGPT/OpenAI) backend. */
enum class AiProviderKind { LOCAL, OPENAI }

/**
 * A selectable AI provider shown in the ticket-generator / chat picker. [key] is the stable
 * identifier persisted as the last-used selection; [label] is what the user sees.
 */
data class AiProviderChoice(
    val key: String,
    val label: String,
    val kind: AiProviderKind,
)

/** Stable provider keys (persisted, so keep them constant). */
object AiProviderKeys {
    const val LOCAL = "local"
    const val OPENAI = "openai"
}
