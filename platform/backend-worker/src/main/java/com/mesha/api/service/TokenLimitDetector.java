package com.mesha.api.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects whether an AI provider message indicates that a token/context/usage limit was hit.
 *
 * <p>Blocks proxies several underlying agents (claude, codex, ghagpt/GitHub Copilot, gemini),
 * each of which surfaces limit conditions with different wording. The patterns below are derived
 * from the real user-facing strings those providers emit, for example:
 * <ul>
 *   <li>Claude / Claude Code: "You've hit your limit · resets 4:40pm (UTC)",
 *       "Usage limit reached · resets at 5:40 PM", "Claude usage limit reached. Your limit will reset at 3pm"</li>
 *   <li>Codex (ChatGPT plan): "You've hit your usage limit."</li>
 *   <li>Gemini: "The input token count (X) exceeds the maximum number of tokens allowed (Y)",
 *       "429 RESOURCE_EXHAUSTED", "You exceeded your current quota"</li>
 *   <li>GitHub Copilot (ghagpt): "prompt token count of X exceeds the limit of Y",
 *       "You have exceeded your premium request allowance"</li>
 * </ul>
 *
 * <p>Shared by the polling path ({@code SessionPollTransactions}) and the webhook path
 * ({@code BlocksSessionService}) so both fire the {@code AI_TOKEN_LIMIT_HIT} automation trigger
 * consistently.
 */
public final class TokenLimitDetector {

    private static final Pattern TOKEN_LIMIT_PATTERN = Pattern.compile(
            // Generic token/context limit terms
            "token[_ ]limit|context[_ ]limit|context[_ ]length|max[_ ]tokens|out[_ ]of[_ ]tokens|context[_ ]window"
            // Claude.ai usage limit: "You've hit your limit · resets 4:40pm (UTC)"
            + "|hit your limit"
            // Claude.ai alternate: "You're out of messages until <time>"
            + "|out of messages"
            // Claude API context overflow: "prompt is too long"
            + "|prompt is too long"
            // Claude API / OpenAI rate limiting: "rate limit reached", "rate_limit_error"
            + "|rate.?limit"
            // Gemini: "The input token count (X) exceeds the maximum number of tokens allowed (Y)"
            + "|maximum number of tokens allowed|input token count"
            // Gemini quota/rate errors: "RESOURCE_EXHAUSTED", "Resource has been exhausted",
            // "Quota exceeded for quota metric"
            + "|resource.{0,20}exhausted|quota.{0,5}exceeded|resource_exhausted"
            // GitHub Copilot (ghagpt): "prompt token count of X exceeds the limit of Y"
            + "|prompt token count"
            // GitHub Copilot (ghagpt): "You have exceeded your premium request allowance",
            // "Premium requests 100%" — no "quota"/"limit"/"reached" token, so match on the
            // Copilot-specific "premium request" / "request allowance" phrasing directly.
            + "|premium.{0,15}request|request.{0,5}allowance"
            // OpenAI/ChatGPT: "insufficient_quota", "exceeded your current quota"
            + "|insufficient.quota|exceeded.{0,30}quota"
            // Various providers: "token count exceeds maximum"
            + "|token count exceeds"
            // General usage/daily/monthly limit phrases:
            // "usage limit", "usage_limit", "your usage limit has been reached"
            + "|usage.{0,10}limit"
            // "limit reached", "monthly limit reached", "daily limit reached"
            + "|limit.{0,10}reached"
            // Anthropic billing: "Your credit balance is too low to access the Claude API"
            + "|credit.{0,30}too.{0,10}low",
            Pattern.CASE_INSENSITIVE);

    private TokenLimitDetector() {
    }

    /** Returns true when the given message text matches a known token/usage-limit pattern. */
    public static boolean isTokenLimitMessage(String message) {
        return message != null && TOKEN_LIMIT_PATTERN.matcher(message).find();
    }

    /** Returns true when any message in the list matches a known token/usage-limit pattern. */
    public static boolean anyMessageIsTokenLimit(List<String> messages) {
        return messages != null && messages.stream().anyMatch(TokenLimitDetector::isTokenLimitMessage);
    }
}
