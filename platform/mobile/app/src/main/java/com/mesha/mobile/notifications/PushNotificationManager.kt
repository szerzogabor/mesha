package com.mesha.mobile.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.mesha.mobile.data.repository.MeshaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Coordinates push-notification registration: keeps the backend's device-token registry
 * in sync with the user's on/off preference and the current FCM token.
 *
 * Turning notifications on registers this device's token; turning them off unregisters it,
 * so a disabled device receives no pushes server-side (belt-and-braces with the client-side
 * check when a message arrives). All backend calls require an authenticated Clerk session,
 * so registration is (re)synced once the app reaches an authenticated state.
 */
@Singleton
class PushNotificationManager @Inject constructor(
    private val repository: MeshaRepository,
    private val preferences: NotificationPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _enabled = MutableStateFlow(preferences.enabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isEnabled(): Boolean = preferences.enabled

    /** Persist the toggle and register/unregister this device's token accordingly. */
    suspend fun setEnabled(value: Boolean) {
        preferences.enabled = value
        _enabled.value = value
        val token = currentToken() ?: return
        if (value) {
            repository.registerDevice(token)
        } else {
            repository.unregisterDevice(token)
        }
    }

    /**
     * Ensure the backend has this device's token when notifications are enabled. Called
     * after sign-in; safe to call repeatedly (registration is an idempotent upsert).
     */
    suspend fun syncRegistration() {
        if (!preferences.enabled) return
        val token = currentToken() ?: return
        repository.registerDevice(token)
    }

    /** Invoked by the messaging service when FCM rotates the token. */
    fun onNewToken(token: String) {
        if (!preferences.enabled) return
        scope.launch { repository.registerDevice(token) }
    }

    /** Fetch the current FCM token, or null if Firebase is not configured/unavailable. */
    private suspend fun currentToken(): String? {
        if (!FirebaseBootstrap.isReady) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!cont.isActive) return@addOnCompleteListener
                    if (task.isSuccessful) {
                        cont.resume(task.result)
                    } else {
                        Log.w(TAG, "Failed to fetch FCM token", task.exception)
                        cont.resume(null)
                    }
                }
        }
    }

    private companion object {
        const val TAG = "PushNotificationManager"
    }
}
