package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.HabitFlowApp
import com.example.ui.theme.HabitFlowTheme
import com.example.viewmodel.HabitViewModel

class MainActivity : ComponentActivity() {
    private val habitViewModel: HabitViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permission result handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Widget DataObserver
        com.example.widget.WidgetRepositoryProvider.startObserving(this)

        // Handle widget deep link
        val widgetTargetTab = intent.getStringExtra("widget_target_tab")
        if (widgetTargetTab != null) {
            habitViewModel.handleWidgetDeepLink(widgetTargetTab)
        }

        // Request notification permission on Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by habitViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by habitViewModel.dynamicColor.collectAsStateWithLifecycle()

            HabitFlowTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HabitFlowApp(viewModel = habitViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val widgetTargetTab = intent.getStringExtra("widget_target_tab")
        if (widgetTargetTab != null) {
            habitViewModel.handleWidgetDeepLink(widgetTargetTab)
        }
    }
}
