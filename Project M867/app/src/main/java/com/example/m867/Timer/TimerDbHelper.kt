package com.example.m867.Timer

import TimerStats
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TimerDbHelper(context: Context) : SQLiteOpenHelper(context, "timer.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE timer_stats (
                user_id TEXT PRIMARY KEY,
                total_focus_minutes INTEGER NOT NULL DEFAULT 0,
                completed_sessions INTEGER NOT NULL DEFAULT 0,
                current_streak INTEGER NOT NULL DEFAULT 0,
                max_streak INTEGER NOT NULL DEFAULT 0,
                perfect_sessions INTEGER NOT NULL DEFAULT 0,
                sessions_with_1_ticket INTEGER NOT NULL DEFAULT 0,
                sessions_with_2_tickets INTEGER NOT NULL DEFAULT 0,
                sessions_with_3plus_tickets INTEGER NOT NULL DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS timer_stats")
        onCreate(db)
    }

    fun getStats(userId: String): TimerStats? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM timer_stats WHERE user_id = ?", arrayOf(userId))

        return if (cursor.moveToFirst()) {
            TimerStats(
                userId = userId,
                totalFocusMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("total_focus_minutes")),
                completedSessions = cursor.getInt(cursor.getColumnIndexOrThrow("completed_sessions")),
                currentStreak = cursor.getInt(cursor.getColumnIndexOrThrow("current_streak")),
                maxStreak = cursor.getInt(cursor.getColumnIndexOrThrow("max_streak")),
                perfectSessions = cursor.getInt(cursor.getColumnIndexOrThrow("perfect_sessions")),
                sessionsWith1Ticket = cursor.getInt(cursor.getColumnIndexOrThrow("sessions_with_1_ticket")),
                sessionsWith2Tickets = cursor.getInt(cursor.getColumnIndexOrThrow("sessions_with_2_tickets")),
                sessionsWith3PlusTickets = cursor.getInt(cursor.getColumnIndexOrThrow("sessions_with_3plus_tickets"))
            )
        } else {
            null
        }.also { cursor.close() }
    }

    fun updateStats(stats: TimerStats) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("user_id", stats.userId)
            put("total_focus_minutes", stats.totalFocusMinutes)
            put("completed_sessions", stats.completedSessions)
            put("current_streak", stats.currentStreak)
            put("max_streak", stats.maxStreak)
            put("perfect_sessions", stats.perfectSessions)
            put("sessions_with_1_ticket", stats.sessionsWith1Ticket)
            put("sessions_with_2_tickets", stats.sessionsWith2Tickets)
            put("sessions_with_3plus_tickets", stats.sessionsWith3PlusTickets)
        }

        db.insertWithOnConflict("timer_stats", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

}

