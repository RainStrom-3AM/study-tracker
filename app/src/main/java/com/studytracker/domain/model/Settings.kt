package com.studytracker.domain.model

data class AppSettings(
    val dailyGoalMinutes: Int = 120,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val darkModeOption: DarkModeOption = DarkModeOption.SYSTEM
)

enum class DarkModeOption {
    SYSTEM, LIGHT, DARK
}
