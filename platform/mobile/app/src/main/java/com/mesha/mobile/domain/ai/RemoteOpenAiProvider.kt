package com.mesha.mobile.domain.ai

import com.mesha.mobile.data.remote.MeAiApi
import com.mesha.mobile.data.remote.dto.MeAiDraftResponseDto
import com.mesha.mobile.data.remote.dto.MeAiPromptRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [LocalAiProvider] that generates via the user's own OpenAI/ChatGPT credential by calling
 * the backend (`/api/me/ai/*`), instead of an on-device model. The credential is configured
 * once on the web; this provider just uses it. Despite the interface name, "local" here means
 * "the app's pluggable AI provider" — the interface doc explicitly anticipates a remote one.
 *
 * HTTP/network failures are translated to [LocalAiException] so the existing UI error handling
 * applies unchanged. A 412 means the user hasn't connected OpenAI.
 */
@Singleton
class RemoteOpenAiProvider @Inject constructor(
    private val meAiApi: MeAiApi,
    private val json: Json,
) : LocalAiProvider {

    override val id: String = "openai"
    override val displayName: String = "ChatGPT"

    override suspend fun isAvailable(): Boolean = try {
        meAiApi.getOpenAiConfig()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 404 (not configured) or any transport error → treat as unavailable.
        false
    }

    override suspend fun generateIssueDraft(request: GenerateIssueRequest): IssueDraft = call {
        val dto = meAiApi.generateDraft(MeAiPromptRequestDto(request.prompt))
        dto.toIssueDraft(request.prompt)
    }

    override suspend fun generate(prompt: String): String = call {
        meAiApi.complete(MeAiPromptRequestDto(prompt)).text.trim()
    }

    override suspend fun generateChatResponse(history: List<LocalChatMessage>): String = call {
        val prompt = history.joinToString("\n") { msg ->
            val role = if (msg.role == LocalChatMessage.Role.USER) "User" else "Assistant"
            "$role: ${msg.content}"
        } + "\nAssistant:"
        meAiApi.complete(MeAiPromptRequestDto(prompt)).text.trim()
    }

    /** Run a suspending backend call, mapping failures to [LocalAiException]. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() == 412) {
            throw LocalAiException.ModelNotAvailable(
                "ChatGPT isn't connected. Add your OpenAI credential in the web app settings.",
            )
        }
        throw LocalAiException.InferenceFailed("ChatGPT request failed (HTTP ${e.code()}).", e)
    } catch (e: LocalAiException) {
        throw e
    } catch (e: Exception) {
        throw LocalAiException.InferenceFailed("Couldn't reach ChatGPT: ${e.message ?: "network error"}.", e)
    }

    private fun MeAiDraftResponseDto.toIssueDraft(fallbackTitleSource: String): IssueDraft {
        val title = title.trim().ifBlank {
            fallbackTitleSource.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(140)
        }
        if (title.isBlank() && description.isBlank()) {
            throw LocalAiException.InvalidOutput("ChatGPT returned an empty draft")
        }
        return IssueDraft(
            title = title.take(140),
            description = description.trim(),
            acceptanceCriteria = acceptanceCriteria
                .split('\n')
                .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("[ ]").trim() }
                .filter { it.isNotBlank() }
                .distinct(),
            priority = IssuePriority.fromLenient(prioritySuggestion),
            labels = parseLabels(suggestedLabels),
        )
    }

    /** `suggestedLabels` arrives as a JSON array string (e.g. `["backend","api"]`). */
    private fun parseLabels(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            (json.parseToJsonElement(raw) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                .orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
