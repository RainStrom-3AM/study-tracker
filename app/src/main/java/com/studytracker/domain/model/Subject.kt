package com.studytracker.domain.model

data class Subject(
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    val isDefault: Boolean = false
)
