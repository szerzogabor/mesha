package com.mesha.mobile.localai.model

/**
 * A catalog [model] joined with its on-device install [status] — the unit the UI renders for
 * each row on the Local AI screen.
 */
data class CatalogEntry(
    val model: LocalAiModel,
    val status: ModelStatus,
)

/** Install state of a catalog model relative to what is on disk. */
sealed interface ModelStatus {

    /** Not present on the device. */
    data object NotInstalled : ModelStatus

    /** Installed and matching the catalog version. */
    data class Installed(val installed: InstalledModel) : ModelStatus

    /** Installed, but the catalog advertises a newer [LocalAiModel.version]. */
    data class UpdateAvailable(val installed: InstalledModel) : ModelStatus
}
