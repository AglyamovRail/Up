package com.example.m867.Habit

import android.content.Context
import android.util.Log
import com.example.m867.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HabitRepository(private val context: Context) {
    private val db             = FirebaseFirestore.getInstance()
    private val habitsCol      = db.collection("habits")
    private val recordsCol     = db.collection("habit_records")
    private val dbHelper       = HabitDbHelper(context)

    // =============== Habits ===============

    suspend fun addHabit(habit: Habit) {
        dbHelper.addHabit(habit)
        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                habitsCol.document(habit.id)
                    .set(habit.toFirebaseMap())
                    .await()
            } catch (e: Exception) {
                Log.w("HabitRepo", "addHabit failed", e)
            }
        }
    }

    suspend fun updateHabit(habit: Habit) {
        dbHelper.addHabit(habit) // Уже поддерживает все поля
        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                habitsCol.document(habit.id)
                    .update(habit.toUpdateMap())
                    .await()
            } catch (e: Exception) {
                Log.w("HabitRepo", "updateHabit failed", e)
            }
        }
    }

    suspend fun deleteHabit(habitId: String) {
        dbHelper.deleteHabit(habitId)
        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                habitsCol.document(habitId).delete().await()
            } catch (e: Exception) {
                Log.w("HabitRepo", "deleteHabit failed", e)
            }
        }
    }

    suspend fun syncHabits(userId: String) {
        if (!NetworkUtils.isNetworkAvailable(context)) return

        // 1. Локальные → сервер (только новые/измененные привычки)
        val localHabits = dbHelper.getHabits(userId)
        localHabits.forEach { habit ->
            try {
                // Используем merge, чтобы сохранить возможные серверные изменения
                habitsCol.document(habit.id)
                    .set(habit.toFirebaseMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w("HabitRepo", "Ошибка синхронизации привычки ${habit.id}", e)
            }
        }

        // 2. Сервер → локальные (добавляем отсутствующие)
        try {
            val serverHabits = habitsCol.whereEqualTo("userId", userId).get().await()
            serverHabits.documents.mapNotNull { it.toObject(Habit::class.java)?.copy(id = it.id) }
                .forEach { serverHabit ->
                    if (localHabits.none { it.id == serverHabit.id }) {
                        dbHelper.addHabit(serverHabit)
                    }
                }
        } catch (e: Exception) {
            Log.w("HabitRepo", "Ошибка загрузки привычек", e)
        }
    }

    private fun Habit.toFirebaseMap() = mapOf(
        "name" to name,
        "description" to description,
        "colorHex" to colorHex,
        "userId" to userId,
        "periodType" to periodType,
        "periodDays" to periodDays,
        "createdAt" to createdAt,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun Habit.toUpdateMap() = mapOf(
        "name" to name,
        "description" to description,
        "colorHex" to colorHex,
        "periodType" to periodType,
        "periodDays" to periodDays,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    // =============== Records ===============

    suspend fun addRecord(record: HabitRecord) {
        dbHelper.addOrUpdateHabitRecord(record)
        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                recordsCol.document(record.id)
                    .set(record.toFirebaseMap())
                    .await()
            } catch (e: Exception) {
                Log.w("HabitRepo", "addRecord failed", e)
            }
        }
    }

    suspend fun deleteRecord(record: HabitRecord) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.w("HabitRepo", "User not authenticated")
            return
        }

        dbHelper.deleteHabitRecord(record.habitId, record.date)
        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                // Альтернативный способ удаления - сначала найти документ по полям
                val query = recordsCol
                    .whereEqualTo("habitId", record.habitId)
                    .whereEqualTo("date", record.date)
                    .whereEqualTo("userId", record.userId)
                    .limit(1)

                val snapshot = query.get().await()
                if (!snapshot.isEmpty) {
                    snapshot.documents.first().reference.delete().await()
                }
            } catch (e: Exception) {
                Log.w("HabitRepo", "deleteRecord failed", e)
            }
        }
    }

    // В методе syncRecords в HabitRepository.kt
    suspend fun syncRecords(userId: String) {
        if (!NetworkUtils.isNetworkAvailable(context)) return

        // 1. Синхронизация локальных записей
        val localRecords = dbHelper.getAllRecords().filter { it.userId == userId }
        localRecords.forEach { record ->
            try {
                // Проверяем, должна ли эта дата учитываться для привычки
                val habit = dbHelper.getHabits(userId).find { it.id == record.habitId }
                if (habit != null) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = dateFormat.parse(record.date)
                    val createdDate = Date(habit.createdAt.seconds * 1000)

                    if (date != null && isDayActiveForHabit(
                            date,
                            createdDate,
                            habit.periodType,
                            habit.periodDays
                        )) {
                        recordsCol.document(record.id)
                            .set(record.toFirebaseMap(), SetOptions.merge())
                            .await()
                    }
                }
            } catch (e: Exception) {
                Log.e("SYNC", "Ошибка синхронизации записи ${record.id}", e)
            }
        }

        // 2. Загрузка серверных записей
        try {
            val serverRecords = recordsCol
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(HabitRecord::class.java)?.copy(id = it.id) }

            serverRecords.forEach { serverRecord ->
                val habit = dbHelper.getHabits(userId).find { it.id == serverRecord.habitId }
                if (habit != null) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = dateFormat.parse(serverRecord.date)
                    val createdDate = Date(habit.createdAt.seconds * 1000)

                    if (date != null && isDayActiveForHabit(
                            date,
                            createdDate,
                            habit.periodType,
                            habit.periodDays
                        )) {
                        val existsLocally = dbHelper.getHabitRecordsForHabit(serverRecord.habitId)
                            .any { it.date == serverRecord.date }

                        if (!existsLocally) {
                            dbHelper.addOrUpdateHabitRecord(serverRecord)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SYNC", "Ошибка загрузки записей с сервера", e)
        }
    }

    private fun isDayActiveForHabit(
        date: Date,
        createdDate: Date,
        periodType: Int,
        periodDays: Int
    ): Boolean {
        val daysDiff = ((date.time - createdDate.time) / (1000 * 60 * 60 * 24)).toInt()
        return when (periodType) {
            0 -> true // каждый день
            1 -> daysDiff % 2 == 0 // через день
            2 -> daysDiff % periodDays == 0 // раз в N дней
            else -> true
        }
    }


    private fun HabitRecord.toFirebaseMap() = mapOf(
        "habitId" to habitId,
        "date" to date,
        "isCompleted" to isCompleted,
        "userId" to userId,
        "lastUpdated" to FieldValue.serverTimestamp()
    )
}
