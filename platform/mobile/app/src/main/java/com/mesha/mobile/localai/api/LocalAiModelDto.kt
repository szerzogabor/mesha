package com.mesha.mobile.localai.api

import com.mesha.mobile.localai.model.LocalAiModel
import kotlinx.serialization.Serializable

/**
 * Wire representation of a catalog entry returned by `GET /api/local-ai/models`.
 * Mirrors the backend `LocalAiModelDto` record field-for-field.
 */
@Serializable
data class LocalAiModelDto(
    val id: String,
    val name: String,
    val provider: String = "",
    val source: String = "",
    val version: String = "",
    val engine: String = "",
    val fileName: String,
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val downloadUrl: String,
    val resolveUrl: String? = null,
    val licenseUrl: String? = null,
    val minimumRamGb: Int = 0,
    val minimumStorageGb: Int = 0,
    val recommended: Boolean = false,
) {
    fun toDomain(): LocalAiModel = LocalAiModel(
        id = id,
        name = name,
        provider = provider,
        source = source,
        version = version,
        engine = engine,
        fileName = fileName,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        downloadUrl = downloadUrl,
        resolveUrl = resolveUrl,
        licenseUrl = licenseUrl,
        minimumRamGb = minimumRamGb,
        minimumStorageGb = minimumStorageGb,
        recommended = recommended,
    )
}
