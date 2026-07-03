package com.mesha.api.model;

/**
 * How a user's OpenAI credential authenticates.
 *
 * <ul>
 *   <li>{@link #API_KEY} — a standard {@code sk-...} key calling {@code api.openai.com}
 *       (billed per token usage).</li>
 *   <li>{@link #CHATGPT_TOKEN} — a ChatGPT-subscription OAuth credential (access +
 *       refresh token + account id) extracted from Codex desktop's
 *       {@code ~/.codex/auth.json}, calling the ChatGPT backend (no per-token billing).</li>
 * </ul>
 */
public enum OpenAiAuthMode {
    API_KEY,
    CHATGPT_TOKEN
}
