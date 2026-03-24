package com.memorandum

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.memorandum.navigation.AppNavGraph
import com.memorandum.navigation.NavigationRequest
import com.memorandum.ui.theme.MemorandumTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var navigationNonce by mutableLongStateOf(0L)
    private var navigationRequest by mutableStateOf<NavigationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationRequest = extractNavigationRequest(intent)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MemorandumTheme {
                AppNavGraph(
                    initialNavigationRequest = navigationRequest,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationRequest = extractNavigationRequest(intent)
    }

    private fun extractNavigationRequest(intent: Intent?): NavigationRequest? {
        val destination = intent?.getStringExtra("navigate_to") ?: return null
        navigationNonce += 1
        return NavigationRequest(
            destination = destination,
            taskId = intent.getStringExtra("task_id"),
            nonce = navigationNonce,
        )
    }
}
