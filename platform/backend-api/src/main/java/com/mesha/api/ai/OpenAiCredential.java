package com.mesha.api.ai;

import com.mesha.api.model.OpenAiAuthMode;

/**
 * Decrypted, ready-to-use OpenAI credential passed to {@link OpenAiAIDraftGenerator}.
 * For {@link OpenAiAuthMode#API_KEY} only {@code apiKey} is set; for
 * {@link OpenAiAuthMode#CHATGPT_TOKEN} {@code accessToken} (and {@code accountId})
 * are set, already refreshed if needed. {@code model} may be null (generator falls
 * back to a per-mode default).
 */
public record OpenAiCredential(
    OpenAiAuthMode authMode,
    String apiKey,
    String accessToken,
    String accountId,
    String model
) {}
