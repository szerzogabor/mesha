package com.mesha.mobile.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists whether the user wants ticket push notifications, toggled from Settings.
 * Backed by SharedPreferences (matching [com.mesha.mobile.domain.ai.AiProviderPreferences]).
 * Defaults to enabled; the actual OS-level permission is requested separately.
 */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    private companion object {
        const val PREFS_NAME = "notification_prefs"
        const val KEY_ENABLED = "ticket_notifications_enabled"
    }
}
