package com.mesha.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt-enabled and provides the [HiltWorkerFactory] so
 * WorkManager can construct injected workers (e.g. [com.mesha.mobile.data.sync.DraftSyncWorker]).
 */
@HiltAndroidApp
class MeshaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
