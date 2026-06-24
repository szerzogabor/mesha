package com.mesha.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.clerk.api.Clerk
import com.clerk.api.ClerkConfigurationOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt-enabled and provides the [HiltWorkerFactory] so
 * WorkManager can construct injected workers (e.g. [com.mesha.mobile.data.sync.DraftSyncWorker]).
 *
 * Also initializes the Clerk Android SDK, which owns the sign-in UI ([com.clerk.ui.auth.AuthView],
 * rendered from [com.mesha.mobile.ui.screens.login.LoginScreen]) and session persistence;
 * [com.mesha.mobile.data.repository.AuthRepository] mirrors its session state into the app.
 */
@HiltAndroidApp
class MeshaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        Clerk.initialize(
            this,
            BuildConfig.CLERK_PUBLISHABLE_KEY,
            options = ClerkConfigurationOptions(enableDebugMode = BuildConfig.DEBUG),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
