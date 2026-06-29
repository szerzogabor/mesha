package com.mesha.mobile.localai.api

import kotlinx.serialization.Serializable

/** Wire representation of `GET /api/local-ai/models/{id}/resolve`. */
@Serializable
data class ResolveDownloadUrlDto(
    val url: String,
)
