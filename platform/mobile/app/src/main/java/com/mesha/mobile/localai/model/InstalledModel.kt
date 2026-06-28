package com.mesha.mobile.localai.model

import kotlinx.serialization.Serializable

/**
 * Metadata for a model that has been downloaded, verified and installed on the device.
 *
 * Persisted as `metadata.json` inside the model's directory so the set of installed models
 * survives app restarts and even reinstalls of the catalog — the filesystem is the source of
 * truth for what is installed, mirroring the storage layout documented for the feature.
 */
@Serializable
data class InstalledModel(
    val id: String,
    val name: String,
    val provider: String,
    val version: String,
    val engine: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val installedAtEpochMs: Long,
)
