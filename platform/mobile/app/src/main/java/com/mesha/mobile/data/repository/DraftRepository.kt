package com.mesha.mobile.data.repository

import android.util.Log
import com.mesha.mobile.data.local.draft.DraftDao
import com.mesha.mobile.data.local.draft.DraftEntity
import com.mesha.mobile.data.local.draft.SyncStatus
import com.mesha.mobile.data.remote.dto.CreateIssueRequestDto
import com.mesha.mobile.domain.ai.IssueDraft
import com.mesha.mobile.domain.ai.IssuePriority
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first store for AI-generated issue drafts.
 *
 * Drafts are produced fully on-device and persisted immediately, so the user can create
 * them with no network. Synchronization to the Mesha backend is a separate, retryable
 * step driven by [syncPending] (invoked by the UI on demand and by [DraftSyncWorker] when
 * connectivity returns).
 */
@Singleton
class DraftRepository @Inject constructor(
    private val draftDao: DraftDao,
    private val meshaRepository: MeshaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeDrafts(): Flow<List<DraftEntity>> = draftDao.observeAll()
    fun observeUnsyncedCount(): Flow<Int> = draftDao.observeUnsyncedCount()

    suspend fun getDraft(id: String): DraftEntity? = draftDao.getById(id)

    /** Persist a freshly generated (or user-edited) draft for a target project. */
    suspend fun saveDraft(
        draft: IssueDraft,
        projectId: String,
        workspaceId: String,
        prompt: String,
        existingId: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val existing = existingId?.let { draftDao.getById(it) }
        val entity = DraftEntity(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            workspaceId = workspaceId,
            prompt = prompt,
            title = draft.title,
            description = draft.description,
            acceptanceCriteriaJson = encodeList(draft.acceptanceCriteria),
            priority = draft.priority.name,
            labelsJson = encodeList(draft.labels),
            syncStatus = SyncStatus.PENDING,
            syncAttempts = existing?.syncAttempts ?: 0,
            lastError = null,
            remoteIssueId = existing?.remoteIssueId,
            remoteIssueIdentifier = existing?.remoteIssueIdentifier,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        draftDao.upsert(entity)
        return entity.id
    }

    suspend fun deleteDraft(id: String) = draftDao.delete(id)

    fun toIssueDraft(entity: DraftEntity): IssueDraft = IssueDraft(
        title = entity.title,
        description = entity.description,
        acceptanceCriteria = decodeList(entity.acceptanceCriteriaJson),
        priority = IssuePriority.fromLenient(entity.priority),
        labels = decodeList(entity.labelsJson),
    )

    /**
     * Attempt to sync all syncable drafts. Returns the number successfully created.
     * Each draft transitions PENDING/FAILED → SYNCING → SYNCED|FAILED; attempts are
     * capped so a permanently-bad draft does not retry forever.
     */
    suspend fun syncPending(): Int {
        var created = 0
        for (draft in draftDao.getSyncable(MAX_ATTEMPTS)) {
            if (syncOne(draft)) created++
        }
        return created
    }

    /** Whether any drafts remain eligible for a sync attempt — drives WorkManager retry. */
    suspend fun hasSyncableDrafts(): Boolean = draftDao.getSyncable(MAX_ATTEMPTS).isNotEmpty()

    /** Force a sync attempt for one draft regardless of attempt count (e.g. user tap). */
    suspend fun retry(id: String): Boolean {
        val draft = draftDao.getById(id) ?: return false
        return syncOne(draft)
    }

    private suspend fun syncOne(draft: DraftEntity): Boolean {
        draftDao.update(draft.copy(syncStatus = SyncStatus.SYNCING, updatedAt = System.currentTimeMillis()))

        // Map label names → label ids for the target workspace. If the label lookup
        // itself fails (e.g. transient network), abort and leave the draft for retry
        // rather than creating an issue that silently drops the user's labels.
        val labelIds = resolveLabelIds(draft)
        if (labelIds == null) {
            draftDao.update(
                draft.copy(
                    syncStatus = SyncStatus.FAILED,
                    syncAttempts = draft.syncAttempts + 1,
                    lastError = "Could not load workspace labels",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            return false
        }

        val request = CreateIssueRequestDto(
            title = draft.title,
            description = buildDescription(draft),
            priority = draft.priority,
            labelIds = labelIds.ifEmpty { null },
        )

        return meshaRepository.createIssue(draft.projectId, request).fold(
            onSuccess = { issue ->
                draftDao.update(
                    draft.copy(
                        syncStatus = SyncStatus.SYNCED,
                        remoteIssueId = issue.id,
                        remoteIssueIdentifier = issue.identifier,
                        lastError = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                true
            },
            onFailure = { e ->
                Log.w(TAG, "Draft sync failed for ${draft.id}", e)
                draftDao.update(
                    draft.copy(
                        syncStatus = SyncStatus.FAILED,
                        syncAttempts = draft.syncAttempts + 1,
                        lastError = e.message,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                false
            },
        )
    }

    /** @return resolved label ids, or null if the label lookup failed (caller retries). */
    private suspend fun resolveLabelIds(draft: DraftEntity): List<String>? {
        val names = decodeList(draft.labelsJson)
        if (names.isEmpty()) return emptyList()
        val result = meshaRepository.getLabels(draft.workspaceId)
        if (result.isFailure) return null
        val byName = result.getOrNull().orEmpty().associateBy { it.name.lowercase() }
        // Unknown label names are dropped (no matching id); a failed lookup returns null above.
        return names.mapNotNull { byName[it.lowercase()]?.id }
    }

    /** Acceptance criteria are appended to the description as a checklist on creation. */
    private fun buildDescription(draft: DraftEntity): String {
        val criteria = decodeList(draft.acceptanceCriteriaJson)
        if (criteria.isEmpty()) return draft.description
        val checklist = criteria.joinToString("\n") { "- [ ] $it" }
        return buildString {
            append(draft.description)
            append("\n\n## Acceptance Criteria\n")
            append(checklist)
        }
    }

    private fun encodeList(list: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), list)

    private fun decodeList(raw: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(emptyList())

    private companion object {
        const val TAG = "DraftRepository"
        const val MAX_ATTEMPTS = 5
    }
}
