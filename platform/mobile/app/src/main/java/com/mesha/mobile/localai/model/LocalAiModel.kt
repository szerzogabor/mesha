package com.mesha.mobile.localai.model

/**
 * A supported on-device AI model as described by the Mesha backend catalog.
 *
 * Deliberately engine- and provider-agnostic: [engine] and [source] are plain strings so the
 * same type can describe a MediaPipe `.task` bundle from Hugging Face today and an ONNX,
 * GGUF (llama.cpp) or MLC artifact from any other provider tomorrow without a code change.
 * The mobile app never hardcodes download URLs — every field here originates from the backend.
 */
data class LocalAiModel(
    val id: String,
    val name: String,
    val provider: String,
    val source: String,
    val version: String,
    val engine: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val resolveUrl: String? = null,
    val licenseUrl: String? = null,
    val minimumRamGb: Int = 0,
    val minimumStorageGb: Int = 0,
    val recommended: Boolean = false,
)
