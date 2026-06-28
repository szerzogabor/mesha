package com.mesha.mobile.localai.api

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit binding to the backend Local AI model catalog. Kept separate from [com.mesha.mobile
 * .data.remote.MeshaApi] so the Local AI module stays self-contained, but it shares the same
 * Retrofit/OkHttp stack (see `LocalAiModule`).
 */
interface LocalAiApi {

    /** The full catalog of supported on-device models. */
    @GET("api/local-ai/models")
    suspend fun getModels(): List<LocalAiModelDto>

    /** A single catalog entry by id. */
    @GET("api/local-ai/models/{id}")
    suspend fun getModel(@Path("id") id: String): LocalAiModelDto
}
