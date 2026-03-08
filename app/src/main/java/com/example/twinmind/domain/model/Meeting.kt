package com.example.twinmind.domain.model

data class Meeting(
    val id: String,
    val dateStarted: Long,
    val title: String,
    val summary: Summary?,
    val isCompleted: Boolean
)