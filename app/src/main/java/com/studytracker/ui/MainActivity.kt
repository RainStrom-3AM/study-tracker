package com.studytracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.studytracker.data.repository.StudyRepository
import com.studytracker.domain.model.DarkModeOption
import com.studytracker.ui.navigation.StudyTrackerNavGraph
import com.studytracker.ui.theme.StudyTrackerTheme
import com.studytracker.worker.StudyReminderWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: StudyRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
        scheduleReminderIfNeeded()

        setContent {
            val settings by repository.getSettings().collectAsStateWithLifecycle(initialValue = null)
            val darkModeOption = settings?.darkModeOption ?: "SYSTEM"

            StudyTrackerTheme(darkModeOption = darkModeOption) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StudyTrackerNavGraph()
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun scheduleReminderIfNeeded() {
        lifecycleScope.launch {
            val settings = repository.getSettings().firstOrNull()
            if (settings != null && settings.reminderEnabled) {
                StudyReminderWorker.schedule(
                    this@MainActivity,
                    settings.reminderHour,
                    settings.reminderMinute
                )
            }
        }
    }
}
