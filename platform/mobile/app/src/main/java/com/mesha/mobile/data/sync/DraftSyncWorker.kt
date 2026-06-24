package com.mesha.mobile.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mesha.mobile.data.repository.DraftRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains the offline draft queue when connectivity is available. Enqueued whenever a
 * draft is created/edited and on app start; WorkManager guarantees it runs once the
 * network constraint is met, surviving process death. Retries are handled both by
 * WorkManager (worker-level) and by the per-draft attempt counter in [DraftRepository].
 */
@HiltWorker
class DraftSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val draftRepository: DraftRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        draftRepository.syncPending()
        // syncPending swallows per-draft errors, so ask the repository whether any
        // drafts still need syncing; if so, let WorkManager back off and retry.
        if (draftRepository.hasSyncableDrafts()) Result.retry() else Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "draft-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DraftSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
