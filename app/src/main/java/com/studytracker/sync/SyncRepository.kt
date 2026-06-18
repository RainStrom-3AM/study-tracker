package com.studytracker.sync

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.JsonObject
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
    private val gson = Gson()
    private var cachedBinId: String? = null

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER}_${Build.MODEL}".replace(" ", "_")
    }

    private fun getBinName(): String {
        return "studytracker_${getDeviceId()}"
    }

    private suspend fun findOrCreateBin(): String? {
        cachedBinId?.let { return it }

        try {
            val listResponse = api.listBins(SyncConfig.MASTER_KEY, SyncConfig.ACCESS_KEY)
            val existing = listResponse.bins?.find { it.name == getBinName() }
            if (existing != null) {
                cachedBinId = existing.id
                return existing.id
            }

            val emptyData = JsonObject().apply {
                addProperty("deviceId", getDeviceId())
                addProperty("deviceInfo", getDeviceInfo())
                add("sessions", com.google.gson.JsonArray())
                add("subjects", com.google.gson.JsonArray())
                add("settings", JsonObject())
            }

            val createResponse = api.createBin(
                masterKey = SyncConfig.MASTER_KEY,
                accessKey = SyncConfig.ACCESS_KEY,
                binName = getBinName(),
                body = emptyData
            )
            cachedBinId = createResponse.metadata?.id
            return cachedBinId
        } catch (_: Exception) {
            return null
        }
    }

    suspend fun pushData() {
        try {
            val binId = findOrCreateBin() ?: return

            val sessions = repository.getSessionsBetweenSync(0, System.currentTimeMillis())
            val subjects = repository.getAllSubjectsSync()
            val settings = repository.getSettingsSync()

            val data = JsonObject().apply {
                addProperty("deviceId", getDeviceId())
                addProperty("deviceInfo", getDeviceInfo())
                addProperty("lastSync", System.currentTimeMillis())
                add("sessions", gson.toJsonTree(sessions.map { s ->
                    mapOf(
                        "id" to s.id,
                        "subjectId" to s.subjectId,
                        "startTime" to s.startTime,
                        "endTime" to s.endTime,
                        "durationSeconds" to s.durationSeconds,
                        "notes" to s.notes
                    )
                }))
                add("subjects", gson.toJsonTree(subjects.map { s ->
                    mapOf(
                        "id" to s.id,
                        "name" to s.name,
                        "colorHex" to s.colorHex,
                        "isDefault" to s.isDefault,
                        "orderIndex" to s.orderIndex
                    )
                }))
                add("settings", gson.toJsonTree(settings?.let { s ->
                    mapOf(
                        "dailyGoalMinutes" to s.dailyGoalMinutes,
                        "reminderEnabled" to s.reminderEnabled,
                        "reminderHour" to s.reminderHour,
                        "reminderMinute" to s.reminderMinute,
                        "darkModeOption" to s.darkModeOption
                    )
                }))
            }

            api.updateBin(SyncConfig.MASTER_KEY, SyncConfig.ACCESS_KEY, binId, data)
        } catch (_: Exception) { }
    }

    suspend fun pullData(): Boolean {
        try {
            val binId = findOrCreateBin() ?: return false

            val response = api.readBin(SyncConfig.MASTER_KEY, SyncConfig.ACCESS_KEY, binId)
            val record = response.record?.asJsonObject ?: return false

            val sessionsList = record.getAsJsonArray("sessions") ?: return false
            val subjectsList = record.getAsJsonArray("subjects") ?: return false
            val settingsObj = record.getAsJsonObject("settings")

            if (subjectsList.size() > 0) {
                val existingSubjects = repository.getAllSubjectsSync()
                if (existingSubjects.isEmpty()) {
                    subjectsList.forEach { el ->
                        val s = el.asJsonObject
                        repository.insertSubject(
                            SubjectEntity(
                                id = s.get("id")?.asLong ?: 0L,
                                name = s.get("name")?.asString ?: "",
                                colorHex = s.get("colorHex")?.asString ?: "#9E9E9E",
                                isDefault = s.get("isDefault")?.asBoolean ?: false,
                                orderIndex = s.get("orderIndex")?.asInt ?: 0
                            )
                        )
                    }
                }
            }

            if (sessionsList.size() > 0) {
                val existingSessions = repository.getSessionsBetweenSync(0, System.currentTimeMillis())
                if (existingSessions.isEmpty()) {
                    sessionsList.forEach { el ->
                        val s = el.asJsonObject
                        repository.insertSession(
                            SessionEntity(
                                id = s.get("id")?.asLong ?: 0L,
                                subjectId = s.get("subjectId")?.asLong ?: 0L,
                                startTime = s.get("startTime")?.asLong ?: 0L,
                                endTime = s.get("endTime")?.asLong ?: 0L,
                                durationSeconds = s.get("durationSeconds")?.asInt ?: 0,
                                notes = s.get("notes")?.asString ?: ""
                            )
                        )
                    }
                }
            }

            if (settingsObj != null && settingsObj.size() > 0) {
                val existingSettings = repository.getSettingsSync()
                if (existingSettings == null) {
                    repository.updateSettings(
                        SettingsEntity(
                            dailyGoalMinutes = settingsObj.get("dailyGoalMinutes")?.asInt ?: 120,
                            reminderEnabled = settingsObj.get("reminderEnabled")?.asBoolean ?: false,
                            reminderHour = settingsObj.get("reminderHour")?.asInt ?: 9,
                            reminderMinute = settingsObj.get("reminderMinute")?.asInt ?: 0,
                            darkModeOption = settingsObj.get("darkModeOption")?.asString ?: "SYSTEM"
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
