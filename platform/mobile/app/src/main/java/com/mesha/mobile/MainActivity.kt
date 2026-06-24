package com.mesha.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mesha.mobile.data.sync.DraftSyncWorker
import com.mesha.mobile.ui.MeshaApp
import com.mesha.mobile.ui.theme.MeshaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Drain any drafts queued while offline as soon as connectivity allows.
        DraftSyncWorker.enqueue(applicationContext)

        setContent {
            MeshaTheme {
                MeshaApp()
            }
        }
    }
}
