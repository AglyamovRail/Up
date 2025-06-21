package com.example.m867.Score

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.firebase.Timestamp

class ScoreDbHelper(context: Context) : SQLiteOpenHelper(context, "scores.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE user_scores (
                userId TEXT PRIMARY KEY,
                points INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS user_scores")
        onCreate(db)
    }

    fun upsertScore(score: UserScore) {
        val values = ContentValues().apply {
            put("userId", score.userId)
            put("points", score.points)
            put("lastUpdated", score.lastUpdated.seconds)
        }
        writableDatabase.insertWithOnConflict(
            "user_scores",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getScore(userId: String): UserScore? {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM user_scores WHERE userId = ?",
            arrayOf(userId)
        )
        return if (cursor.moveToFirst()) {
            UserScore(
                userId = cursor.getString(cursor.getColumnIndexOrThrow("userId")),
                points = cursor.getInt(cursor.getColumnIndexOrThrow("points")),
                lastUpdated = Timestamp(
                    cursor.getLong(cursor.getColumnIndexOrThrow("lastUpdated")),
                    0
                )
            )
        } else {
            null
        }.also { cursor.close() }
    }
}