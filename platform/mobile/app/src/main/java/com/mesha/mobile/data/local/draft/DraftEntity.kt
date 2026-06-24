package com.mesha.mobile.data.local.draft

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A locally-persisted issue draft. Drafts are created entirely on-device (offline-capable)
 * and queued for synchronization to the Mesha backend. The [syncStatus] state machine
 * drives the retry/sync worker.
 */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    /** Target project the issue will be created in once synced. */
    val projectId: String,
    val workspaceId: String,

    val prompt: String,
    val title: String,
    val description: String,
    /** Acceptance criteria + labels stored as JSON arrays (see DraftJson). */
    val acceptanceCriteriaJson: String,
    val priority: String,
    val labelsJson: String,

    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val syncAttempts: Int = 0,
    val lastError: String? = null,
    /** Identifier of the issue created on the server once synced. */
    val remoteIssueId: String? = null,
    val remoteIssueIdentifier: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class SyncStatus {
    /** Draft created locally, awaiting review or sync. */
    PENDING,

    /** Currently being pushed to the backend. */
    SYNCING,

    /** Successfully created on the backend. */
    SYNCED,

    /** Sync failed; eligible for retry. */
    FAILED,
}
