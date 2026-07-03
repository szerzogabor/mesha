package com.mesha.api.dto;

import com.mesha.api.model.OpenAiAuthMode;

/**
 * Request to connect/update a user's OpenAI credential.
 *
 * <p>For {@link OpenAiAuthMode#API_KEY} only {@code apiKey} is required. For
 * {@link OpenAiAuthMode#CHATGPT_TOKEN} the {@code accessToken} (and, to survive
 * expiry, {@code refreshToken} + {@code accountId}) are taken from Codex desktop's
 * {@code ~/.codex/auth.json}. {@code model} is an optional override.
 */
public record SaveOpenAiConfigRequest(
    OpenAiAuthMode authMode,
    String apiKey,
    String accessToken,
    String refreshToken,
    String accountId,
    String model
) {}
