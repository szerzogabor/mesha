package com.mesha.mobile.data.repository

import com.mesha.mobile.data.local.draft.DraftDao
import com.mesha.mobile.data.local.draft.DraftEntity
import com.mesha.mobile.data.local.draft.SyncStatus
import com.mesha.mobile.data.remote.dto.IssueDto
import com.mesha.mobile.domain.ai.IssueDraft
import com.mesha.mobile.domain.ai.IssuePriority
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DraftRepositoryTest {

    private lateinit var dao: FakeDraftDao
    private lateinit var mesha: MeshaRepository
    private lateinit var repository: DraftRepository

    private val draft = IssueDraft(
        title = "Add uninstall handling",
        description = "Handle GitHub App uninstall",
        acceptanceCriteria = listOf("Webhook handled", "Rows removed"),
        priority = IssuePriority.HIGH,
        labels = listOf("backend"),
    )

    @Before
    fun setUp() {
        dao = FakeDraftDao()
        mesha = mockk(relaxed = true)
        repository = DraftRepository(dao, mesha)
    }

    @Test
    fun saveDraft_persistsPendingDraft() = runTest {
        val id = repository.saveDraft(draft, "proj-1", "ws-1", "prompt")
        val saved = dao.getById(id)!!
        assertEquals(SyncStatus.PENDING, saved.syncStatus)
        assertEquals("Add uninstall handling", saved.title)
        assertEquals("proj-1", saved.projectId)
    }

    @Test
    fun syncPending_marksDraftSyncedOnSuccess() = runTest {
        coEvery { mesha.getLabels(any()) } returns Result.success(emptyList())
        coEvery { mesha.createIssue(any(), any()) } returns Result.success(
            issueDto(id = "issue-9", identifier = "ENG-9"),
        )

        val id = repository.saveDraft(draft, "proj-1", "ws-1", "prompt")
        val created = repository.syncPending()

        assertEquals(1, created)
        val synced = dao.getById(id)!!
        assertEquals(SyncStatus.SYNCED, synced.syncStatus)
        assertEquals("issue-9", synced.remoteIssueId)
        assertEquals("ENG-9", synced.remoteIssueIdentifier)
        assertNull(synced.lastError)
    }

    @Test
    fun syncPending_marksFailedAndIncrementsAttemptsOnError() = runTest {
        coEvery { mesha.getLabels(any()) } returns Result.success(emptyList())
        coEvery { mesha.createIssue(any(), any()) } returns
            Result.failure(RuntimeException("network down"))

        val id = repository.saveDraft(draft, "proj-1", "ws-1", "prompt")
        val created = repository.syncPending()

        assertEquals(0, created)
        val failed = dao.getById(id)!!
        assertEquals(SyncStatus.FAILED, failed.syncStatus)
        assertEquals(1, failed.syncAttempts)
        assertEquals("network down", failed.lastError)
    }

    @Test
    fun syncPending_retriesFailedDraftAndEventuallySucceeds() = runTest {
        coEvery { mesha.getLabels(any()) } returns Result.success(emptyList())
        coEvery { mesha.createIssue(any(), any()) } returns
            Result.failure(RuntimeException("offline"))

        val id = repository.saveDraft(draft, "proj-1", "ws-1", "prompt")
        repository.syncPending() // fails once

        coEvery { mesha.createIssue(any(), any()) } returns
            Result.success(issueDto(id = "issue-1"))
        val created = repository.syncPending() // retried, succeeds

        assertEquals(1, created)
        assertEquals(SyncStatus.SYNCED, dao.getById(id)!!.syncStatus)
    }

    @Test
    fun syncPending_skipsDraftsThatExceededMaxAttempts() = runTest {
        coEvery { mesha.getLabels(any()) } returns Result.success(emptyList())
        coEvery { mesha.createIssue(any(), any()) } returns
            Result.failure(RuntimeException("permanent"))

        val id = repository.saveDraft(draft, "proj-1", "ws-1", "prompt")
        // Exhaust attempts (MAX_ATTEMPTS = 5).
        repeat(6) { repository.syncPending() }

        val entity = dao.getById(id)!!
        assertTrue(entity.syncAttempts >= 5)
        // Further sync runs do nothing because attempts cap is reached.
        assertEquals(0, repository.syncPending())
    }

    private fun issueDto(id: String, identifier: String? = null) = IssueDto(
        id = id,
        projectId = "proj-1",
        identifier = identifier,
        title = "Add uninstall handling",
    )

    /** Minimal in-memory DraftDao for deterministic tests. */
    private class FakeDraftDao : DraftDao {
        private val store = MutableStateFlow<Map<String, DraftEntity>>(emptyMap())

        override fun observeAll(): Flow<List<DraftEntity>> =
            store.map { it.values.sortedByDescending { d -> d.updatedAt } }

        override fun observeUnsynced(): Flow<List<DraftEntity>> =
            store.map { it.values.filter { d -> d.syncStatus != SyncStatus.SYNCED } }

        override suspend fun getById(id: String): DraftEntity? = store.value[id]

        override suspend fun getSyncable(maxAttempts: Int): List<DraftEntity> =
            store.value.values
                .filter { it.syncStatus in setOf(SyncStatus.PENDING, SyncStatus.FAILED) && it.syncAttempts < maxAttempts }
                .sortedBy { it.createdAt }

        override suspend fun upsert(draft: DraftEntity) {
            store.value = store.value + (draft.id to draft)
        }

        override suspend fun update(draft: DraftEntity) {
            store.value = store.value + (draft.id to draft)
        }

        override suspend fun delete(id: String) {
            store.value = store.value - id
        }

        override fun observeUnsyncedCount(): Flow<Int> =
            store.map { it.values.count { d -> d.syncStatus != SyncStatus.SYNCED } }
    }
}
