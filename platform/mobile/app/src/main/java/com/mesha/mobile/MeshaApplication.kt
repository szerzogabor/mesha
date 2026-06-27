package com.mesha.mobile

import android.app.Application
import android.util.Log
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
        val clerkKey = BuildConfig.CLERK_PUBLISHABLE_KEY
        // TEMPORARY DEVELOPMENT FIX: Hardcode the Clerk key for debugging purposes.
        // TODO: Remove this temporary fix and restore proper Gradle configuration.
        val keyToUse = if (clerkKey.isBlank()) {
            "PROVIDE_YOUR_CLERK_PUBLISHABLE_KEY_HERE"
        } else {
            clerkKey
        }
        
        if (keyToUse.isBlank()) {
            Log.e(TAG, "CLERK_PUBLISHABLE_KEY is blank; this build can't sign in.")
            return
        }
        runCatching {
            Clerk.initialize(
                this,
                keyToUse,
                options = ClerkConfigurationOptions(enableDebugMode = BuildConfig.DEBUG),
            )
        }.onSuccess {
            ClerkBootstrap.isReady = true
        }.onFailure { e ->
            Log.e(TAG, "Clerk.initialize failed", e)
        }
    }

    private companion object {
        const val TAG = "MeshaApplication"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
