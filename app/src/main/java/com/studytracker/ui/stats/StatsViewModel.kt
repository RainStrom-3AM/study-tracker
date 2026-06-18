package com.studytracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studytracker.data.repository.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class DailyStudyData(
    val label: String,
    val totalMinutes: Float
)

data class SubjectStudyData(
    val name: String,
    val colorHex: String,
    val totalSeconds: Int
)

sealed class StatsUiState {
    data object Loading : StatsUiState()
    data class Success(
        val todayMinutes: Int = 0,
        val weekMinutes: Int = 0,
        val monthMinutes: Int = 0,
        val allTimeMinutes: Int = 0,
        val dailyGoalMinutes: Int = 120,
        val last7Days: List<DailyStudyData> = emptyList(),
        val subjectBreakdown: List<SubjectStudyData> = emptyList(),
        val currentStreak: Int = 0,
        val longestStreak: Int = 0
    ) : StatsUiState()
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            val settings = repository.getSettingsSync()
            val dailyGoal = settings?.dailyGoalMinutes ?: 120

            val now = System.currentTimeMillis()
            val todayStart = repository.getStartOfDay()
            val weekStart = repository.getStartOfWeek()
            val monthStart = repository.getStartOfMonth()

            val todaySeconds = repository.getTotalDurationBetweenSync(todayStart, now)
            val weekSeconds = repository.getTotalDurationBetweenSync(weekStart, now)
            val monthSeconds = repository.getTotalDurationBetweenSync(monthStart, now)

            val allSessions = repository.getSessionsBetweenSync(0, now)
            val allTimeSeconds = allSessions.sumOf { it.durationSeconds }

            val calendar = Calendar.getInstance()
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val last7Days = mutableListOf<DailyStudyData>()

            for (i in 6 downTo 0) {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayStart = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val dayEnd = cal.timeInMillis

                val daySeconds = repository.getTotalDurationBetweenSync(dayStart, dayEnd)
                last7Days.add(
                    DailyStudyData(
                        label = dayFormat.format(Date(dayStart)),
                        totalMinutes = daySeconds / 60f
                    )
                )
            }

            val subjects = repository.getAllSubjectsSync()
            val subjectBreakdown = subjects.mapNotNull { subject ->
                val subjectSessions = allSessions.filter { it.subjectId == subject.id }
                val total = subjectSessions.sumOf { it.durationSeconds }
                if (total > 0) {
                    SubjectStudyData(
                        name = subject.name,
                        colorHex = subject.colorHex,
                        totalSeconds = total
                    )
                } else null
            }.sortedByDescending { it.totalSeconds }

            val allDates = repository.getDistinctStudyDatesSince(0)
            val streaks = calculateStreaks(allDates)

            _uiState.value = StatsUiState.Success(
                todayMinutes = todaySeconds / 60,
                weekMinutes = weekSeconds / 60,
                monthMinutes = monthSeconds / 60,
                allTimeMinutes = allTimeSeconds / 60,
                dailyGoalMinutes = dailyGoal,
                last7Days = last7Days,
                subjectBreakdown = subjectBreakdown,
                currentStreak = streaks.first,
                longestStreak = streaks.second
            )
        }
    }

    private fun calculateStreaks(dates: List<String>): Pair<Int, Int> {
        if (dates.isEmpty()) return Pair(0, 0)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sortedDates = dates.mapNotNull {
            try { dateFormat.parse(it) } catch (e: Exception) { null }
        }.sortedDescending()

        if (sortedDates.isEmpty()) return Pair(0, 0)

        var currentStreak = 0
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (i in sortedDates.indices) {
            val expected = Calendar.getInstance().apply {
                time = today.time
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val studyCal = Calendar.getInstance().apply {
                time = sortedDates[i]
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (isSameDay(studyCal, expected)) {
                currentStreak++
            } else {
                break
            }
        }

        var longestStreak = 1
        var tempStreak = 1
        for (i in 1 until sortedDates.size) {
            val diff = (sortedDates[i - 1].time - sortedDates[i].time) / (1000 * 60 * 60 * 24)
            if (diff == 1L) {
                tempStreak++
                longestStreak = maxOf(longestStreak, tempStreak)
            } else if (diff > 1) {
                tempStreak = 1
            }
        }

        return Pair(currentStreak, longestStreak)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
