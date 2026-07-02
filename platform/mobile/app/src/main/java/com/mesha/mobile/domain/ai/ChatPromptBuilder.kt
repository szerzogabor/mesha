package com.mesha.mobile.domain.ai

/**
 * Formats a multi-turn conversation history into a single prompt string for Gemma-family models.
 *
 * Uses the standard Gemma instruction-tuned turn format so the model can distinguish
 * prior exchanges from the new user message it must respond to.
 */
object ChatPromptBuilder {

    private const val USER_START = "<start_of_turn>user\n"
    private const val MODEL_START = "<start_of_turn>model\n"
    private const val TURN_END = "<end_of_turn>\n"

    /**
     * Builds a prompt from [history]. The last entry must be a USER message — this is the
     * turn the model is expected to reply to. An open model turn tag is appended so the
     * model continues from there.
     *
     * @throws IllegalArgumentException if history is empty or the last message is not USER.
     */
    fun build(history: List<LocalChatMessage>): String {
        require(history.isNotEmpty()) { "Conversation history must not be empty" }
        require(history.last().role == LocalChatMessage.Role.USER) {
            "Last message in history must be a USER message"
        }

        return buildString {
            for (message in history) {
                when (message.role) {
                    LocalChatMessage.Role.USER -> {
                        append(USER_START)
                        append(message.content.trim())
                        append("\n")
                        append(TURN_END)
                    }
                    LocalChatMessage.Role.ASSISTANT -> {
                        append(MODEL_START)
                        append(message.content.trim())
                        append("\n")
                        append(TURN_END)
                    }
                }
            }
            // Open model turn so the model completes from here.
            append(MODEL_START)
        }
    }
}
