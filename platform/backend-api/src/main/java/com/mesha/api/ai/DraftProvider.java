package com.mesha.api.ai;

/**
 * Which AI provider the user picked in the "Generate with AI" modal.
 *
 * <ul>
 *   <li>{@link #DEFAULT} — legacy behavior: Blocks if the workspace has it
 *       connected, otherwise the server-side Claude fallback.</li>
 *   <li>{@link #BLOCKS} — force the workspace Blocks agent.</li>
 *   <li>{@link #OPENAI} — the requesting user's own OpenAI / ChatGPT credential.</li>
 * </ul>
 */
public enum DraftProvider {
    DEFAULT,
    BLOCKS,
    OPENAI
}
