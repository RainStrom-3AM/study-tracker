package com.studytracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.studytracker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class StudyTimerService : Service() {

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var timerJob: Job? = null
    private var startTimestamp: Long = 0L
    private var accumulatedSeconds: Int = 0

    inner class TimerBinder : Binder() {
        fun getService(): StudyTimerService = this@StudyTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val subjectName = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "Study"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1, createNotification(subjectName, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(1, createNotification(subjectName, 0))
                }
                startTimer(subjectName)
            }
            ACTION_STOP -> {
                stopTimer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    fun startTimer(subjectName: String) {
        if (_isRunning.value) return
        _isRunning.value = true
        startTimestamp = System.currentTimeMillis()

        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (_isRunning.value) {
                    accumulatedSeconds++
                    _elapsedSeconds.value = accumulatedSeconds
                    updateNotification(subjectName, accumulatedSeconds)
                }
            }
        }
    }

    fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        timerJob = null
    }

    fun resumeTimer(subjectName: String) {
        if (_isRunning.value) return
        _isRunning.value = true

        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (_isRunning.value) {
                    accumulatedSeconds++
                    _elapsedSeconds.value = accumulatedSeconds
                    updateNotification(subjectName, accumulatedSeconds)
                }
            }
        }
    }

    fun stopTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        timerJob = null
    }

    fun resetTimer() {
        stopTimer()
        accumulatedSeconds = 0
        _elapsedSeconds.value = 0
    }

    fun getElapsedSeconds(): Int = accumulatedSeconds

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Study Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the current study session timer"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(subjectName: String, seconds: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StudyTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        val timeString = String.format("%02d:%02d:%02d", hours, minutes, secs)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Studying: $subjectName")
            .setContentText("Elapsed: $timeString")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(subjectName: String, seconds: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, createNotification(subjectName, seconds))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "study_timer_channel"
        const val ACTION_START = "com.studytracker.ACTION_START"
        const val ACTION_STOP = "com.studytracker.ACTION_STOP"
        const val EXTRA_SUBJECT_NAME = "subject_name"

        fun startService(context: Context, subjectName: String) {
            val intent = Intent(context, StudyTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SUBJECT_NAME, subjectName)
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, StudyTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
