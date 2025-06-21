package com.example.m867.Habit

import com.google.firebase.Timestamp

data class Habit(
    var id: String = "",
    val name: String = "",
    val description: String = "",
    val colorHex: String = "#4CAF50",
    val userId: String = "",
    val periodType: Int = 0,
    val periodDays: Int = 1,
    val createdAt: Timestamp = Timestamp.now(),
) {
    constructor() : this(id = "", name = "", description = "", colorHex = "#4CAF50", userId = "", periodType = 0, periodDays = 1, createdAt = Timestamp.now())
}
