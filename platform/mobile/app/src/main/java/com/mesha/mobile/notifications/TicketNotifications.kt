package com.mesha.mobile.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mesha.mobile.MainActivity
import com.mesha.mobile.R

/**
 * Builds and posts ticket status-change notifications, and owns the notification channel.
 * Data-only FCM messages are rendered here so the app fully controls presentation and can
 * respect the user's on/off preference even for a message already in flight.
 */
object TicketNotifications {

    const val CHANNEL_ID = "ticket_status"

    const val EXTRA_ISSUE_ID = "mesha.notification.issueId"
    const val EXTRA_PROJECT_ID = "mesha.notification.projectId"

    /** Idempotent — safe to call on every app start. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_ticket_updates),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_ticket_updates_desc)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Post a "ticket moved to <status>" notification. Tapping it opens the ticket via
     * [MainActivity]. No-op if the runtime notification permission is not granted.
     */
    fun showStatusChanged(
        context: Context,
        issueId: String,
        projectId: String,
        identifier: String?,
        title: String,
        status: String,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val prefix = if (!identifier.isNullOrBlank()) "$identifier " else ""
        val headline = context.getString(
            R.string.notif_ticket_status_title,
            prefix,
            humanReadableStatus(status),
        )

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ISSUE_ID, issueId)
            putExtra(EXTRA_PROJECT_ID, projectId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            issueId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(headline)
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(issueId.hashCode(), notification)
    }

    /** "IN_PROGRESS" -> "In Progress". */
    private fun humanReadableStatus(status: String): String =
        status.split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
            .ifBlank { status }
}
