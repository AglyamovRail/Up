package com.example.m867.Habit

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HabitDbHelper(context: Context) : SQLiteOpenHelper(context, "habits.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")

        db.execSQL("""CREATE TABLE habits (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT,
            colorHex TEXT NOT NULL,
            userId TEXT NOT NULL,
            periodType INTEGER NOT NULL DEFAULT 0,
            periodDays INTEGER NOT NULL DEFAULT 1,
            createdAt INTEGER NOT NULL
        )""")

        db.execSQL("""CREATE TABLE habit_records (
            id TEXT PRIMARY KEY,
            habitId TEXT NOT NULL,
            date TEXT NOT NULL,
            isCompleted INTEGER NOT NULL,
            FOREIGN KEY (habitId) REFERENCES habits(id) ON DELETE CASCADE ON UPDATE NO ACTION
        )""")
    }

    companion object {
        const val DATABASE_NAME = "habits.db"
        const val DATABASE_VERSION = 1
        const val TABLE_HABITS = "habits"
        const val TABLE_RECORDS = "habit_records"
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE habits ADD COLUMN periodType INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE habits ADD COLUMN periodDays INTEGER NOT NULL DEFAULT 1")
        }
        db.execSQL("DROP TABLE IF EXISTS habit_records")
        db.execSQL("DROP TABLE IF EXISTS habits")
        onCreate(db)
    }

    fun addHabit(habit: Habit) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", habit.id)
            put("name", habit.name)
            put("description", habit.description)
            put("colorHex", habit.colorHex)
            put("userId", habit.userId)
            put("periodType", habit.periodType)
            put("periodDays", habit.periodDays)
            put("createdAt", habit.createdAt.seconds)
        }
        db.insertWithOnConflict(TABLE_HABITS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getHabits(userId: String): List<Habit> {
        val habits = mutableListOf<Habit>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM habits WHERE userId = ?", arrayOf(userId))

        if (cursor.moveToFirst()) {
            do {
                habits.add(
                    Habit(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                        colorHex = cursor.getString(cursor.getColumnIndexOrThrow("colorHex")),
                        userId = cursor.getString(cursor.getColumnIndexOrThrow("userId")),
                        periodType = cursor.getInt(cursor.getColumnIndexOrThrow("periodType")),
                        periodDays = cursor.getInt(cursor.getColumnIndexOrThrow("periodDays")),
                        createdAt = Timestamp(cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")), 0)
                    )
                )
            } while (cursor.moveToNext())
        }

        cursor.close()
        return habits
    }

    fun addOrUpdateHabitRecord(record: HabitRecord) {
        val values = ContentValues().apply {
            put("id", record.id)
            put("habitId", record.habitId)
            put("date", record.date)
            put("isCompleted", if (record.isCompleted) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(TABLE_RECORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getHabitRecordsForHabit(habitId: String): List<HabitRecord> {
        val records = mutableListOf<HabitRecord>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM habit_records WHERE habitId = ?", arrayOf(habitId))

        if (cursor.moveToFirst()) {
            do {
                records.add(
                    HabitRecord(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        habitId = cursor.getString(cursor.getColumnIndexOrThrow("habitId")),
                        date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                        isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("isCompleted")) == 1
                    )
                )
            } while (cursor.moveToNext())
        }

        cursor.close()
        return records
    }
    fun updateHabitDate(habitId: String, newDate: Date) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("createdAt", newDate.time / 1000) // Конвертируем в секунды
        }
        db.update(
            "habits",
            values,
            "id = ?",
            arrayOf(habitId)
        )
    }
    fun deleteHabit(habitId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "habit_records",
                "habitId = ?",
                arrayOf(habitId)
            )
            writableDatabase.delete(
                "habits",
                "id = ?",
                arrayOf(habitId)
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun deleteHabitRecord(habitId: String, date: String) {
        writableDatabase.delete(
            "habit_records",
            "habitId = ? AND date = ?",
            arrayOf(habitId, date)
        )
    }

    fun getAllRecords(): List<HabitRecord> {
        val records = mutableListOf<HabitRecord>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM habit_records", null)

        if (cursor.moveToFirst()) {
            do {
                records.add(
                    HabitRecord(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        habitId = cursor.getString(cursor.getColumnIndexOrThrow("habitId")),
                        date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                        isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("isCompleted")) == 1
                    )
                )
            } while (cursor.moveToNext())
        }

        cursor.close()
        return records
    }

    fun getExpectedCompletions(habitId: String): Int {
        val db = readableDatabase

        val cursorHabit = db.rawQuery("""
        SELECT periodType, periodDays, createdAt 
        FROM habits 
        WHERE id = ?
    """, arrayOf(habitId))

        if (!cursorHabit.moveToFirst()) {
            cursorHabit.close()
            return 1
        }

        val periodType = cursorHabit.getInt(0)
        val periodDays = cursorHabit.getInt(1)
        val createdAt = cursorHabit.getLong(2)
        cursorHabit.close()

        val cursorDates = db.rawQuery("""
        SELECT DISTINCT date 
        FROM habit_records 
        WHERE habitId = ?
        ORDER BY date ASC
    """, arrayOf(habitId))

        val datesWithRecords = mutableListOf<String>()
        if (cursorDates.moveToFirst()) {
            do {
                datesWithRecords.add(cursorDates.getString(0))
            } while (cursorDates.moveToNext())
        }
        cursorDates.close()

        if (datesWithRecords.isEmpty()) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val createdDate = Date(createdAt * 1000)
            val today = Date()

            val daysDiff = ((today.time - createdDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1

            return when (periodType) {
                1 -> (daysDiff / 2).coerceAtLeast(1)
                2 -> (daysDiff / periodDays).coerceAtLeast(1)
                else -> daysDiff.coerceAtLeast(1)
            }
        }

        val firstDate = datesWithRecords.first()
        val lastDate = datesWithRecords.last()

        return calculateExpectedCompletionsBetweenDates(
            periodType,
            periodDays,
            createdAt,
            firstDate,
            lastDate
        )
    }

    private fun calculateExpectedCompletionsBetweenDates(
        periodType: Int,
        periodDays: Int,
        createdAtSec: Long,
        firstDate: String,
        lastDate: String
    ): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = dateFormat.parse(firstDate) ?: return 1
        val endDate = dateFormat.parse(lastDate) ?: return 1

        val totalDays = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)).toInt() + 1

        return when (periodType) {
            1 -> (totalDays / 2).coerceAtLeast(1)
            2 -> (totalDays / periodDays).coerceAtLeast(1)
            else -> totalDays.coerceAtLeast(1)
        }
    }

    fun getStreakForHabit(habitId: String): Int {
        val db = readableDatabase

        val cursorHabit = db.rawQuery("""
        SELECT periodType, periodDays, createdAt 
        FROM habits 
        WHERE id = ?
    """, arrayOf(habitId))

        if (!cursorHabit.moveToFirst()) {
            cursorHabit.close()
            return 0
        }

        val periodType = cursorHabit.getInt(0)
        val periodDays = cursorHabit.getInt(1)
        val createdAt = Date(cursorHabit.getLong(2) * 1000)
        cursorHabit.close()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val cursorRecords = db.rawQuery("""
        SELECT date 
        FROM habit_records 
        WHERE habitId = ? AND isCompleted = 1
        ORDER BY date DESC
    """, arrayOf(habitId))

        val completedDates = mutableListOf<String>()
        if (cursorRecords.moveToFirst()) {
            do {
                completedDates.add(cursorRecords.getString(0))
            } while (cursorRecords.moveToNext())
        }
        cursorRecords.close()

        if (completedDates.isEmpty()) return 0

        val lastMarkedDateStr = completedDates.firstOrNull {
            val date = dateFormat.parse(it) ?: return@firstOrNull false
            date <= today
        } ?: return 0

        val lastMarkedDate = dateFormat.parse(lastMarkedDateStr) ?: return 0

        val daysSinceLastCompletion = ((today.time - lastMarkedDate.time) / (1000 * 60 * 60 * 24)).toInt()
        if (daysSinceLastCompletion > periodDays) return 0

        var streak = 1
        var currentDate = lastMarkedDate

        while (true) {
            val prevDate = getPreviousExpectedDate(currentDate, createdAt, periodType, periodDays)
            val prevDateStr = dateFormat.format(prevDate)

            if (completedDates.contains(prevDateStr)) {
                streak++
                currentDate = prevDate
            } else {
                break
            }

            if (currentDate <= createdAt) break
        }

        return streak
    }
    private fun getPreviousExpectedDate(
        currentDate: Date,
        createdDate: Date,
        periodType: Int,
        periodDays: Int
    ): Date {
        val calendar = Calendar.getInstance()
        calendar.time = currentDate

        return when (periodType) {
            0 -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.time
            }
            1 -> {
                calendar.add(Calendar.DAY_OF_YEAR, -2)
                calendar.time
            }
            2 -> {
                calendar.add(Calendar.DAY_OF_YEAR, -periodDays)
                calendar.time
            }
            else -> createdDate
        }
    }

    fun canRestoreStreak(habitId: String): Pair<Boolean, String?> {
        val db = readableDatabase

        val cursorHabit = db.rawQuery("""
        SELECT periodType, periodDays, createdAt 
        FROM habits 
        WHERE id = ?
    """, arrayOf(habitId))

        if (!cursorHabit.moveToFirst()) {
            cursorHabit.close()
            return Pair(false, null)
        }

        val periodType = cursorHabit.getInt(0)
        val periodDays = cursorHabit.getInt(1)
        val createdAt = Date(cursorHabit.getLong(2) * 1000)
        cursorHabit.close()

        val cursorLast = db.rawQuery("""
        SELECT date 
        FROM habit_records 
        WHERE habitId = ? AND isCompleted = 1
        ORDER BY date DESC
        LIMIT 1
    """, arrayOf(habitId))

        if (!cursorLast.moveToFirst()) {
            cursorLast.close()
            return Pair(false, null)
        }
        val lastDateStr = cursorLast.getString(0)
        cursorLast.close()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val lastDate = dateFormat.parse(lastDateStr) ?: return Pair(false, null)

        val expectedPrevDate = when (periodType) {
            0 -> {
                Calendar.getInstance().apply {
                    time = lastDate
                    add(Calendar.DAY_OF_YEAR, -1)
                }.time
            }
            1 -> {
                Calendar.getInstance().apply {
                    time = lastDate
                    add(Calendar.DAY_OF_YEAR, -2)
                }.time
            }
            2 -> {
                Calendar.getInstance().apply {
                    time = lastDate
                    add(Calendar.DAY_OF_YEAR, -periodDays)
                }.time
            }
            else -> null
        } ?: return Pair(false, null)

        if (expectedPrevDate.before(createdAt)) return Pair(false, null)

        val cursorCheck = db.rawQuery("""
        SELECT 1 
        FROM habit_records 
        WHERE habitId = ? AND date = ? AND isCompleted = 1
    """, arrayOf(habitId, dateFormat.format(expectedPrevDate)))

        val exists = cursorCheck.moveToFirst()
        cursorCheck.close()

        return if (!exists) {
            Pair(true, dateFormat.format(expectedPrevDate))
        } else {
            Pair(false, null)
        }
    }

}

