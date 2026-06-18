package com.studytracker.sync

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.studytracker.data.db.SessionEntity
import com.studytracker.data.db.SettingsEntity
import com.studytracker.data.db.SubjectEntity
import com.studytracker.data.repository.StudyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val api: MongoApiService,
    private val repository: StudyRepository,
    @ApplicationContext private val context: Context
) {

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER}_${Build.MODEL}".replace(" ", "_")
    }

    private fun deviceFilter(): Map<String, Any> {
        return mapOf("deviceId" to getDeviceId())
    }

    suspend fun pushData() {
        try {
            val deviceId = getDeviceId()
            val deviceInfo = getDeviceInfo()

            val sessions = repository.getSessionsBetweenSync(0, System.currentTimeMillis())
            val subjects = repository.getAllSubjectsSync()
            val settings = repository.getSettingsSync()

            val doc = mapOf(
                "deviceId" to deviceId,
                "deviceInfo" to deviceInfo,
                "lastSync" to System.currentTimeMillis(),
                "sessions" to sessions.map { s ->
                    mapOf(
                        "id" to s.id,
                        "subjectId" to s.subjectId,
                        "startTime" to s.startTime,
                        "endTime" to s.endTime,
                        "durationSeconds" to s.durationSeconds,
                        "notes" to s.notes
                    )
                },
                "subjects" to subjects.map { s ->
                    mapOf(
                        "id" to s.id,
                        "name" to s.name,
                        "colorHex" to s.colorHex,
                        "isDefault" to s.isDefault,
                        "orderIndex" to s.orderIndex
                    )
                },
                "settings" to settings?.let { s ->
                    mapOf(
                        "dailyGoalMinutes" to s.dailyGoalMinutes,
                        "reminderEnabled" to s.reminderEnabled,
                        "reminderHour" to s.reminderHour,
                        "reminderMinute" to s.reminderMinute,
                        "darkModeOption" to s.darkModeOption
                    )
                }
            )

            api.updateOne(
                MongoConfig.API_KEY,
                MongoUpdate(
                    filter = deviceFilter(),
                    update = mapOf("\$set" to doc),
                    upsert = true
                )
            )
        } catch (_: Exception) { }
    }

    suspend fun pullData(): Boolean {
        try {
            val response = api.findOne(
                MongoConfig.API_KEY,
                MongoFilter(filter = deviceFilter())
            )

            if (response == null || response.isEmpty()) return false

            @Suppress("UNCHECKED_CAST")
            val sessionsList = response["sessions"] as? List<Map<String, Any>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val subjectsList = response["subjects"] as? List<Map<String, Any>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val settingsMap = response["settings"] as? Map<String, Any>

            if (subjectsList.isNotEmpty()) {
                val existingSubjects = repository.getAllSubjectsSync()
                if (existingSubjects.isEmpty()) {
                    subjectsList.forEach { s ->
                        repository.insertSubject(
                            SubjectEntity(
                                id = (s["id"] as? Double)?.toLong() ?: 0L,
                                name = s["name"] as? String ?: "",
                                colorHex = s["colorHex"] as? String ?: "#9E9E9E",
                                isDefault = s["isDefault"] as? Boolean ?: false,
                                orderIndex = (s["orderIndex"] as? Double)?.toInt() ?: 0
                            )
                        )
                    }
                }
            }

            if (sessionsList.isNotEmpty()) {
                val existingSessions = repository.getSessionsBetweenSync(0, System.currentTimeMillis())
                if (existingSessions.isEmpty()) {
                    sessionsList.forEach { s ->
                        repository.insertSession(
                            SessionEntity(
                                id = (s["id"] as? Double)?.toLong() ?: 0L,
                                subjectId = (s["subjectId"] as? Double)?.toLong() ?: 0L,
                                startTime = (s["startTime"] as? Double)?.toLong() ?: 0L,
                                endTime = (s["endTime"] as? Double)?.toLong() ?: 0L,
                                durationSeconds = (s["durationSeconds"] as? Double)?.toInt() ?: 0,
                                notes = s["notes"] as? String ?: ""
                            )
                        )
                    }
                }
            }

            if (settingsMap != null) {
                val existingSettings = repository.getSettingsSync()
                if (existingSettings == null) {
                    repository.updateSettings(
                        SettingsEntity(
                            dailyGoalMinutes = (settingsMap["dailyGoalMinutes"] as? Double)?.toInt() ?: 120,
                            reminderEnabled = settingsMap["reminderEnabled"] as? Boolean ?: false,
                            reminderHour = (settingsMap["reminderHour"] as? Double)?.toInt() ?: 9,
                            reminderMinute = (settingsMap["reminderMinute"] as? Double)?.toInt() ?: 0,
                            darkModeOption = settingsMap["darkModeOption"] as? String ?: "SYSTEM"
                        )
                    )
                }
            }

            return true
        } catch (_: Exception) {
            return false
        }
    }

    suspend fun syncOnStart() {
        pullData()
        pushData()
    }
}
