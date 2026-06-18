package com.studytracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studytracker.data.db.SessionEntity
import com.studytracker.data.db.SubjectEntity
import com.studytracker.data.repository.StudyRepository
import com.studytracker.domain.model.Session
import com.studytracker.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class HistoryUiState(
    val groupedSessions: Map<String, List<Session>> = emptyMap(),
    val subjects: Map<Long, Subject> = emptyMap(),
    val isLoading: Boolean = true,
    val deletedSession: Session? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            repository.getAllSubjects().collect { subjectEntities ->
                val subjectsMap = subjectEntities.associate {
                    it.id to Subject(it.id, it.name, it.colorHex, it.isDefault)
                }
                _uiState.update { it.copy(subjects = subjectsMap) }
            }
        }

        viewModelScope.launch {
            repository.getAllSessions().collect { sessionEntities ->
                val sessions = sessionEntities.map {
                    Session(
                        id = it.id,
                        subjectId = it.subjectId,
                        startTime = it.startTime,
                        endTime = it.endTime,
                        durationSeconds = it.durationSeconds,
                        notes = it.notes
                    )
                }

                val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                val today = Calendar.getInstance()
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

                val grouped = sessions.groupBy { session ->
                    val cal = Calendar.getInstance().apply { timeInMillis = session.startTime }
                    when {
                        isSameDay(cal, today) -> "Today"
                        isSameDay(cal, yesterday) -> "Yesterday"
                        else -> dateFormat.format(Date(session.startTime))
                    }
                }

                _uiState.update {
                    it.copy(
                        groupedSessions = grouped,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletedSession = session) }
            repository.deleteSessionById(session.id)
            _uiState.update {
                it.copy(snackbarMessage = "Session deleted")
            }
        }
    }

    fun undoDelete() {
        val deleted = _uiState.value.deletedSession ?: return
        viewModelScope.launch {
            val entity = SessionEntity(
                id = deleted.id,
                subjectId = deleted.subjectId,
                startTime = deleted.startTime,
                endTime = deleted.endTime,
                durationSeconds = deleted.durationSeconds,
                notes = deleted.notes
            )
            repository.insertSession(entity)
            _uiState.update {
                it.copy(deletedSession = null, snackbarMessage = "Session restored")
            }
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
