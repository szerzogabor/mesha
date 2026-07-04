package com.mesha.mobile.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives FCM messages and the rotating registration token.
 *
 * Ticket status-change messages are sent data-only (see the backend
 * `PushNotificationService`), so the app renders the notification itself here — which also
 * lets it honour the user's on/off preference for a message that was already in flight when
 * they turned notifications off.
 */
@AndroidEntryPoint
class MeshaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushNotificationManager: PushNotificationManager

    override fun onNewToken(token: String) {
        pushNotificationManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != TYPE_STATUS_CHANGED) return
        if (!pushNotificationManager.isEnabled()) return

        val issueId = data["issueId"] ?: return
        val projectId = data["projectId"] ?: return

        TicketNotifications.showStatusChanged(
            context = this,
            issueId = issueId,
            projectId = projectId,
            identifier = data["identifier"],
            title = data["title"].orEmpty(),
            status = data["status"].orEmpty(),
        )
    }

    private companion object {
        const val TYPE_STATUS_CHANGED = "TICKET_STATUS_CHANGED"
    }
}
