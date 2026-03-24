package com.memorandum

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.memorandum.data.repository.NotificationRepository
import com.memorandum.navigation.AppNavGraph
import com.memorandum.navigation.NotificationNavigation
import com.memorandum.scheduler.NotificationHelper
import com.memorandum.ui.theme.MemorandumTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var notificationRepository: NotificationRepository

    private var navigationNonce = 0L
    private var pendingNavigation by mutableStateOf<NotificationNavigation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: action=${intent?.action}, extras=${intent?.extras?.keySet()?.joinToString()}")
        handleLaunchIntent(intent)
        enableEdgeToEdge()
        setContent {
            MemorandumTheme {
                AppNavGraph(
                    pendingNavigation = pendingNavigation,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "onNewIntent: action=${intent.action}, extras=${intent.extras?.keySet()?.joinToString()}")
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val navigation = resolveNavigation(intent)
        Log.i(TAG, "handleLaunchIntent: navigateTo=${navigation?.navigateTo}, taskId=${navigation?.taskId}, nonce=${navigation?.nonce}")
        pendingNavigation = navigation
        markNotificationOpened(intent)
        clearNotificationExtras(intent)
    }

    private fun resolveNavigation(intent: Intent?): NotificationNavigation? {
        return when (intent?.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO)) {
            "task" -> {
                val taskId = intent.getStringExtra(NotificationHelper.EXTRA_TASK_ID)
                if (taskId.isNullOrBlank()) {
                    NotificationNavigation(navigateTo = "today", nonce = nextNavigationNonce())
                } else {
                    NotificationNavigation(navigateTo = "task", taskId = taskId, nonce = nextNavigationNonce())
                }
            }
            "today" -> NotificationNavigation(navigateTo = "today", nonce = nextNavigationNonce())
            else -> null
        }
    }

    private fun nextNavigationNonce(): Long {
        navigationNonce += 1
        return navigationNonce
    }

    private fun markNotificationOpened(intent: Intent?) {
        val notificationId = intent?.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_DB_ID) ?: return
        lifecycleScope.launch {
            notificationRepository.markClicked(notificationId)
        }
    }

    private fun clearNotificationExtras(intent: Intent?) {
        intent?.removeExtra(NotificationHelper.EXTRA_NAVIGATE_TO)
        intent?.removeExtra(NotificationHelper.EXTRA_TASK_ID)
        intent?.removeExtra(NotificationHelper.EXTRA_NOTIFICATION_DB_ID)
    }
}
