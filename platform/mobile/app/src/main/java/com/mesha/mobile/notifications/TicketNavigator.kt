package com.mesha.mobile.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A ticket a notification tap wants the app to open. */
data class TicketRef(val projectId: String, val issueId: String)

/**
 * Bridges a notification tap (handled in [com.mesha.mobile.MainActivity]) to the
 * navigation graph (built inside [com.mesha.mobile.ui.MeshaApp], only after the auth
 * gate). The activity publishes the requested ticket here; the composable observes it and
 * navigates once a [androidx.navigation.NavController] exists, then calls [consume].
 */
object TicketNavigator {
    private val _pending = MutableStateFlow<TicketRef?>(null)
    val pending: StateFlow<TicketRef?> = _pending.asStateFlow()

    fun request(ref: TicketRef) { _pending.value = ref }

    fun consume() { _pending.value = null }
}
