package com.mahood.family

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mahood.family.core.Family
import com.mahood.family.core.sync.Notifier
import com.mahood.family.core.sync.SyncEngine
import com.mahood.family.core.sync.SyncScheduler
import com.mahood.family.core.ui.FamilyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var setUp by mutableStateOf(false)
    private var pendingInvite by mutableStateOf<String?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setUp = Family.isSetUp
        pendingInvite = intent?.dataString

        setContent {
            FamilyTheme {
                if (setUp) {
                    MainScreen(onLeft = { setUp = false })
                } else {
                    SetupScreen(
                        initialInvite = pendingInvite,
                        onDone = {
                            setUp = true
                            SyncScheduler.schedulePeriodic(this)
                            askForNotifications()
                        },
                    )
                }
            }
        }

        // While the app is on screen, sync every 20 seconds so changes feel live.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Notifier.appVisible = true
                try {
                    while (true) {
                        if (Family.isSetUp) SyncEngine.sync(applicationContext)
                        delay(20_000)
                    }
                } finally {
                    Notifier.appVisible = false
                }
            }
        }
        if (setUp) askForNotifications()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { pendingInvite = it }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
