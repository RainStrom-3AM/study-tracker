package com.studytracker.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.studytracker.data.db.SessionEntity
import com.studytracker.data.db.SettingsEntity
import com.studytracker.data.db.SubjectEntity
import com.studytracker.data.repository.StudyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StudyRepository
) {
    private val backupDir = "StudyTracker"
    private val backupPrefix = "study_backup"

    suspend fun createBackup(): Boolean {
        try {
            val sessions = repository.getSessionsBetweenSync(0, System.currentTimeMillis())
            val subjects = repository.getAllSubjectsSync()
            val settings = repository.getSettingsSync()

            val data = JSONObject().apply {
                put("version", 1)
                put("timestamp", System.currentTimeMillis())
                put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

                val sessionsArr = JSONArray()
                sessions.forEach { s ->
                    sessionsArr.put(JSONObject().apply {
                        put("subjectId", s.subjectId)
                        put("startTime", s.startTime)
                        put("endTime", s.endTime)
                        put("durationSeconds", s.durationSeconds)
                        put("notes", s.notes)
                    })
                }
                put("sessions", sessionsArr)

                val subjectsArr = JSONArray()
                subjects.forEach { s ->
                    subjectsArr.put(JSONObject().apply {
                        put("name", s.name)
                        put("colorHex", s.colorHex)
                        put("isDefault", s.isDefault)
                        put("orderIndex", s.orderIndex)
                    })
                }
                put("subjects", subjectsArr)

                settings?.let { s ->
                    put("settings", JSONObject().apply {
                        put("dailyGoalMinutes", s.dailyGoalMinutes)
                        put("reminderEnabled", s.reminderEnabled)
                        put("reminderHour", s.reminderHour)
                        put("reminderMinute", s.reminderMinute)
                        put("darkModeOption", s.darkModeOption)
                    })
                }
            }

            val json = data.toString()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${backupPrefix}_${timestamp}.json"

            saveToDownloads(fileName, json)
            cleanupOldBackups()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun saveToDownloads(fileName: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$backupDir")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(content.toByteArray())
                }
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), backupDir)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { stream ->
                stream.write(content.toByteArray())
            }
        }
    }

    private fun cleanupOldBackups() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%$backupDir%", "$backupPrefix%")
                val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

                val cursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, sortOrder
                )

                cursor?.use {
                    var count = 0
                    while (it.moveToNext()) {
                        count++
                        if (count > 2) {
                            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                            context.contentResolver.delete(uri, null, null)
                        }
                    }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), backupDir)
                if (dir.exists()) {
                    val files = dir.listFiles { f -> f.name.startsWith(backupPrefix) }
                        ?.sortedByDescending { it.lastModified() }
                    files?.drop(2)?.forEach { it.delete() }
                }
            }
        } catch (_: Exception) { }
    }

    suspend fun restoreFromBackup(): Boolean {
        try {
            val latestBackup = findLatestBackup() ?: return false
            val json = JSONObject(latestBackup)

            val subjectsArr = json.optJSONArray("subjects") ?: return false
            val sessionsArr = json.optJSONArray("sessions") ?: return false
            val settingsObj = json.optJSONObject("settings")

            val existingSubjects = repository.getAllSubjectsSync()
            if (existingSubjects.isNotEmpty()) return false

            for (i in 0 until subjectsArr.length()) {
                val s = subjectsArr.getJSONObject(i)
                repository.insertSubject(
                    SubjectEntity(
                        name = s.optString("name", ""),
                        colorHex = s.optString("colorHex", "#9E9E9E"),
                        isDefault = s.optBoolean("isDefault", false),
                        orderIndex = s.optInt("orderIndex", 0)
                    )
                )
            }

            for (i in 0 until sessionsArr.length()) {
                val s = sessionsArr.getJSONObject(i)
                repository.insertSession(
                    SessionEntity(
                        subjectId = s.optLong("subjectId", 0L),
                        startTime = s.optLong("startTime", 0L),
                        endTime = s.optLong("endTime", 0L),
                        durationSeconds = s.optInt("durationSeconds", 0),
                        notes = s.optString("notes", "")
                    )
                )
            }

            if (settingsObj != null && settingsObj.length() > 0) {
                repository.updateSettings(
                    SettingsEntity(
                        dailyGoalMinutes = settingsObj.optInt("dailyGoalMinutes", 120),
                        reminderEnabled = settingsObj.optBoolean("reminderEnabled", false),
                        reminderHour = settingsObj.optInt("reminderHour", 9),
                        reminderMinute = settingsObj.optInt("reminderMinute", 0),
                        darkModeOption = settingsObj.optString("darkModeOption", "SYSTEM")
                    )
                )
            }

            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun findLatestBackup(): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%$backupDir%", "$backupPrefix%")
                val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

                val cursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, sortOrder
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        return context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader().readText()
                        }
                    }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), backupDir)
                if (dir.exists()) {
                    val latest = dir.listFiles { f -> f.name.startsWith(backupPrefix) }
                        ?.maxByOrNull { it.lastModified() }
                    return latest?.readText()
                }
            }
        } catch (_: Exception) { }
        return null
    }

    suspend fun resetAllData() {
        val sessions = repository.getSessionsBetweenSync(0, System.currentTimeMillis())
        sessions.forEach { repository.deleteSession(it) }
        val subjects = repository.getAllSubjectsSync()
        subjects.forEach { repository.deleteSubject(it) }
        deleteAllBackups()
    }

    private fun deleteAllBackups() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%$backupDir%", "$backupPrefix%")

                val cursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, null
                )

                cursor?.use {
                    while (it.moveToNext()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        context.contentResolver.delete(uri, null, null)
                    }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), backupDir)
                dir.listFiles { f -> f.name.startsWith(backupPrefix) }?.forEach { it.delete() }
            }
        } catch (_: Exception) { }
    }
}
