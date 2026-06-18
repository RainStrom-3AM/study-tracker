package com.studytracker.ui.timer

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studytracker.data.db.SessionEntity
import com.studytracker.data.db.SubjectEntity
import com.studytracker.backup.BackupManager
import com.studytracker.data.repository.StudyRepository
import com.studytracker.service.StudyTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TimerState {
    data object Idle : TimerState()
    data object Running : TimerState()
    data object Paused : TimerState()
}

data class TimerUiState(
    val timerState: TimerState = TimerState.Idle,
    val elapsedSeconds: Int = 0,
    val subjects: List<SubjectEntity> = emptyList(),
    val selectedSubject: SubjectEntity? = null,
    val notes: String = "",
    val todayProgressMinutes: Int = 0,
    val dailyGoalMinutes: Int = 120,
    val showSubjectSheet: Boolean = false,
    val sessionSaved: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StudyRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var accumulatedSeconds: Int = 0
    private var sessionStartTime: Long = 0L

    init {
        loadSubjects()
        loadSettings()
        loadTodayProgress()
    }

    private fun loadSubjects() {
        viewModelScope.launch {
            repository.getAllSubjects().collect { subjects ->
                _uiState.update { it.copy(subjects = subjects) }
                if (_uiState.value.selectedSubject == null && subjects.isNotEmpty()) {
                    _uiState.update { it.copy(selectedSubject = subjects.first()) }
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.getSettings().collect { settings ->
                _uiState.update {
                    it.copy(dailyGoalMinutes = settings?.dailyGoalMinutes ?: 120)
                }
            }
        }
    }

    private fun loadTodayProgress() {
        viewModelScope.launch {
            repository.getTodayTotalDuration().collect { seconds ->
                _uiState.update {
                    it.copy(todayProgressMinutes = seconds / 60)
                }
            }
        }
    }

    private fun startTimerJob(subjectName: String) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                accumulatedSeconds++
                _uiState.update { it.copy(elapsedSeconds = accumulatedSeconds) }
            }
        }
    }

    private fun stopTimerJob() {
        timerJob?.cancel()
        timerJob = null
    }

    fun selectSubject(subject: SubjectEntity) {
        _uiState.update { it.copy(selectedSubject = subject, showSubjectSheet = false) }
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun showSubjectSheet() {
        _uiState.update { it.copy(showSubjectSheet = true) }
    }

    fun hideSubjectSheet() {
        _uiState.update { it.copy(showSubjectSheet = false) }
    }

    fun startTimer() {
        val subject = _uiState.value.selectedSubject ?: return
        sessionStartTime = System.currentTimeMillis()
        accumulatedSeconds = 0
        _uiState.update {
            it.copy(
                timerState = TimerState.Running,
                elapsedSeconds = 0,
                sessionSaved = false
            )
        }

        startTimerJob(subject.name)

        val intent = Intent(context, StudyTimerService::class.java).apply {
            action = StudyTimerService.ACTION_START
            putExtra(StudyTimerService.EXTRA_SUBJECT_NAME, subject.name)
        }
        try {
            context.startForegroundService(intent)
        } catch (_: Exception) { }
    }

    fun pauseTimer() {
        _uiState.update { it.copy(timerState = TimerState.Paused) }
        stopTimerJob()
    }

    fun resumeTimer() {
        val subject = _uiState.value.selectedSubject ?: return
        _uiState.update { it.copy(timerState = TimerState.Running) }
        startTimerJob(subject.name)
    }

    fun stopTimer() {
        val state = _uiState.value
        val subject = state.selectedSubject
        val elapsed = accumulatedSeconds

        stopTimerJob()

        if (elapsed > 0 && subject != null) {
            viewModelScope.launch {
                val session = SessionEntity(
                    subjectId = subject.id,
                    startTime = sessionStartTime,
                    endTime = System.currentTimeMillis(),
                    durationSeconds = elapsed,
                    notes = state.notes
                )
                repository.insertSession(session)
                backupManager.createBackup()

                val h = elapsed / 3600
                val m = (elapsed % 3600) / 60
                val s = elapsed % 60
                val timeStr = buildString {
                    if (h > 0) append("${h}h ")
                    if (m > 0) append("${m}m ")
                    append("${s}s")
                }.trim()

                _uiState.update {
                    it.copy(
                        timerState = TimerState.Idle,
                        elapsedSeconds = 0,
                        notes = "",
                        sessionSaved = true,
                        snackbarMessage = "Session saved: $timeStr of ${subject.name}"
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    timerState = TimerState.Idle,
                    elapsedSeconds = 0,
                    notes = ""
                )
            }
        }

        accumulatedSeconds = 0
        StudyTimerService.stopService(context)
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
