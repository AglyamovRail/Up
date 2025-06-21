package com.example.m867.Score

import com.google.firebase.Timestamp

data class UserScore(
    val userId: String = "",
    var points: Int = 0,
    var lastUpdated: Timestamp = Timestamp.Companion.now()
) {
    constructor() : this("", 0, Timestamp.Companion.now())
}