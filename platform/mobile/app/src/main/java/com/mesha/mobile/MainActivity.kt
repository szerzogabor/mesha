package com.mesha.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mesha.mobile.data.sync.DraftSyncWorker
import com.mesha.mobile.notifications.NotificationPreferences
import com.mesha.mobile.notifications.TicketNavigator
import com.mesha.mobile.notifications.TicketNotifications
import com.mesha.mobile.notifications.TicketRef
import com.mesha.mobile.ui.MeshaApp
import com.mesha.mobile.ui.theme.MeshaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var notificationPreferences: NotificationPreferences

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Drain any drafts queued while offline as soon as connectivity allows.
        DraftSyncWorker.enqueue(applicationContext)

        maybeRequestNotificationPermission()
        // A tap on a status-change notification launches/relaunches us with the ticket extras.
        handleNotificationIntent(intent)

        setContent {
            MeshaTheme {
                MeshaApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val issueId = intent?.getStringExtra(TicketNotifications.EXTRA_ISSUE_ID)
        val projectId = intent?.getStringExtra(TicketNotifications.EXTRA_PROJECT_ID)
        if (!issueId.isNullOrBlank() && !projectId.isNullOrBlank()) {
            TicketNavigator.request(TicketRef(projectId = projectId, issueId = issueId))
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!notificationPreferences.enabled) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
