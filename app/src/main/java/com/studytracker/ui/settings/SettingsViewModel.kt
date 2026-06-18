package com.studytracker.ui.settings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studytracker.data.db.SettingsEntity
import com.studytracker.data.db.SubjectEntity
import com.studytracker.data.repository.StudyRepository
import com.studytracker.domain.model.DarkModeOption
import com.studytracker.worker.StudyReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SettingsUiState(
    val dailyGoalMinutes: Int = 120,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val darkModeOption: DarkModeOption = DarkModeOption.SYSTEM,
    val subjects: List<SubjectEntity> = emptyList(),
    val newSubjectName: String = "",
    val newSubjectColor: String = "#4285F4",
    val showAddSubjectDialog: Boolean = false,
    val showTimePicker: Boolean = false,
    val exportMessage: String? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadSubjects()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.getSettings().collect { settings ->
                if (settings != null) {
                    _uiState.update {
                        it.copy(
                            dailyGoalMinutes = settings.dailyGoalMinutes,
                            reminderEnabled = settings.reminderEnabled,
                            reminderHour = settings.reminderHour,
                            reminderMinute = settings.reminderMinute,
                            darkModeOption = try {
                                DarkModeOption.valueOf(settings.darkModeOption)
                            } catch (e: Exception) {
                                DarkModeOption.SYSTEM
                            }
                        )
                    }
                }
            }
        }
    }

    private fun loadSubjects() {
        viewModelScope.launch {
            repository.getAllSubjects().collect { subjects ->
                _uiState.update { it.copy(subjects = subjects) }
            }
        }
    }

    fun updateDailyGoal(minutes: Int) {
        _uiState.update { it.copy(dailyGoalMinutes = minutes) }
        viewModelScope.launch {
            repository.updateDailyGoal(minutes)
        }
    }

    fun toggleReminder(enabled: Boolean) {
        _uiState.update { it.copy(reminderEnabled = enabled) }
        viewModelScope.launch {
            val state = _uiState.value
            repository.updateReminder(enabled, state.reminderHour, state.reminderMinute)
            if (enabled) {
                StudyReminderWorker.schedule(context, state.reminderHour, state.reminderMinute)
            } else {
                StudyReminderWorker.cancel(context)
            }
        }
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(reminderHour = hour, reminderMinute = minute, showTimePicker = false) }
        viewModelScope.launch {
            val state = _uiState.value
            repository.updateReminder(state.reminderEnabled, hour, minute)
            if (state.reminderEnabled) {
                StudyReminderWorker.schedule(context, hour, minute)
            }
        }
    }

    fun showTimePicker() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    fun hideTimePicker() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    fun updateDarkMode(option: DarkModeOption) {
        _uiState.update { it.copy(darkModeOption = option) }
        viewModelScope.launch {
            repository.updateDarkMode(option.name)
        }
    }

    fun updateNewSubjectName(name: String) {
        _uiState.update { it.copy(newSubjectName = name) }
    }

    fun updateNewSubjectColor(color: String) {
        _uiState.update { it.copy(newSubjectColor = color) }
    }

    fun showAddSubjectDialog() {
        _uiState.update { it.copy(showAddSubjectDialog = true, newSubjectName = "", newSubjectColor = "#4285F4") }
    }

    fun hideAddSubjectDialog() {
        _uiState.update { it.copy(showAddSubjectDialog = false) }
    }

    fun addSubject() {
        val name = _uiState.value.newSubjectName.trim()
        val color = _uiState.value.newSubjectColor
        if (name.isBlank()) return

        viewModelScope.launch {
            val currentSubjects = _uiState.value.subjects
            val nextIndex = (currentSubjects.maxOfOrNull { it.orderIndex } ?: 0) + 1
            repository.insertSubject(
                SubjectEntity(name = name, colorHex = color, isDefault = false, orderIndex = nextIndex)
            )
            _uiState.update {
                it.copy(
                    showAddSubjectDialog = false,
                    newSubjectName = "",
                    snackbarMessage = "Subject \"$name\" added"
                )
            }
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        if (subject.isDefault) return
        viewModelScope.launch {
            repository.deleteSubject(subject)
            _uiState.update {
                it.copy(snackbarMessage = "Subject \"${subject.name}\" deleted")
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun exportSessionsToCsv() {
        viewModelScope.launch {
            try {
                val sessions = repository.getSessionsBetweenSync(0, System.currentTimeMillis())
                val subjects = repository.getAllSubjectsSync().associateBy { it.id }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val fileName = "study_sessions_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv"

                val csvContent = buildString {
                    appendLine("Date,Subject,Duration (minutes),Start Time,End Time,Notes")
                    sessions.forEach { session ->
                        val subject = subjects[session.subjectId]?.name ?: "Unknown"
                        val date = dateFormat.format(Date(session.startTime))
                        val startTime = dateFormat.format(Date(session.startTime))
                        val endTime = dateFormat.format(Date(session.endTime))
                        val duration = session.durationSeconds / 60
                        val notes = session.notes.replace(",", ";").replace("\n", " ")
                        appendLine("$date,$subject,$duration,$startTime,$endTime,$notes")
                    }
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    val outputStream: OutputStream? = resolver.openOutputStream(uri)
                    outputStream?.use { stream ->
                        stream.write(csvContent.toByteArray())
                    }
                    _uiState.update {
                        it.copy(exportMessage = "Exported to Downloads/$fileName")
                    }
                } else {
                    _uiState.update {
                        it.copy(exportMessage = "Export failed: could not create file")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(exportMessage = "Export failed: ${e.message}")
                }
            }
        }
    }

    fun dismissExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }
}
