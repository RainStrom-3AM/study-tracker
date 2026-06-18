package com.studytracker.domain.model

data class Session(
    val id: Long = 0,
    val subjectId: Long,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val notes: String = ""
)
