package com.mesha.mobile.domain.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's last-used AI provider choice across app restarts. Backed by
 * SharedPreferences to avoid adding a DataStore dependency for a single string.
 */
@Singleton
class AiProviderPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedKey: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) { prefs.edit().putString(KEY_SELECTED, value).apply() }

    private companion object {
        const val PREFS_NAME = "ai_provider_prefs"
        const val KEY_SELECTED = "selected_provider_key"
    }
}
