package com.example.twinmind.domain.model

data class Summary(
    val title: String,
    val content: String,
    val keyPoints: List<String>,
    val actionItems: List<String>
)