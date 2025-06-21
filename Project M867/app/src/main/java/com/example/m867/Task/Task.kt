package com.example.m867.Task

import com.google.firebase.Timestamp

data class Task(
    var id: String = "",
    val title: String = "",
    val priority: Int = 1,
    val userId: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val isCompleted: Boolean = false,
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val completedAt: Timestamp? = null,
    val updatedAt: Timestamp
) {
    constructor() : this(updatedAt = Timestamp.now())
}