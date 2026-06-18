package com.studytracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.studytracker.service.StudyTimerService
import com.studytracker.worker.StudyReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StudyTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val timerChannel = NotificationChannel(
            StudyTimerService.CHANNEL_ID,
            "Study Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the current study session timer"
            setShowBadge(false)
        }

        val reminderChannel = NotificationChannel(
            StudyReminderWorker.REMINDER_CHANNEL_ID,
            "Study Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily study reminders"
        }

        manager.createNotificationChannel(timerChannel)
        manager.createNotificationChannel(reminderChannel)
    }
}
