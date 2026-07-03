package com.mesha.mobile.data.remote

import com.mesha.mobile.data.remote.dto.MeAiCompletionResponseDto
import com.mesha.mobile.data.remote.dto.MeAiDraftResponseDto
import com.mesha.mobile.data.remote.dto.MeAiPromptRequestDto
import com.mesha.mobile.data.remote.dto.MeOpenAiConfigDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Per-user AI endpoints that generate with the caller's own OpenAI/ChatGPT credential
 * (configured on the web). Auth is attached automatically by [AuthInterceptor].
 *
 * `getOpenAiConfig` returns 200 when ChatGPT is configured and 404 otherwise — callers
 * must treat an HTTP 404 as "not configured". Draft/complete return 412 when the user
 * hasn't connected OpenAI.
 */
interface MeAiApi {

    @GET("api/me/openai/config")
    suspend fun getOpenAiConfig(): MeOpenAiConfigDto

    @POST("api/me/ai/draft")
    suspend fun generateDraft(@Body body: MeAiPromptRequestDto): MeAiDraftResponseDto

    @POST("api/me/ai/complete")
    suspend fun complete(@Body body: MeAiPromptRequestDto): MeAiCompletionResponseDto
}
