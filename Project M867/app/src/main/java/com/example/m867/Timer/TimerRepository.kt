package com.example.m867.Timer

import TimerStats
import android.content.Context
import android.util.Log
import com.example.m867.NetworkUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class TimerRepository(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val timerStatsCol = db.collection("timer_stats")
    private val dbHelper = TimerDbHelper(context)

    suspend fun updateStats(stats: TimerStats) {
        dbHelper.updateStats(stats)

        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                timerStatsCol.document(stats.userId)
                    .set(stats.toFirebaseMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w("TimerRepo", "updateStats failed", e)
            }
        }
    }


    suspend fun getStats(userId: String): TimerStats {
        // Проверяем локальную БД
        dbHelper.getStats(userId)?.let { return it }

        // Если нет в локальной, пробуем загрузить с сервера
        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                val doc = timerStatsCol.document(userId).get().await()
                if (doc.exists()) {
                    val stats = doc.toObject(TimerStats::class.java)!!
                    dbHelper.updateStats(stats)
                    return stats
                }
            } catch (e: Exception) {
                Log.w("TimerRepo", "getStats failed", e)
            }
        }

        // Создаем новую запись если ничего нет
        return TimerStats(userId).also { dbHelper.updateStats(it) }
    }

    suspend fun syncStats(userId: String) {
        if (!NetworkUtils.isNetworkAvailable(context)) return

        try {
            // Сервер → Локальная
            val doc = timerStatsCol.document(userId).get().await()
            if (doc.exists()) {
                val serverStats = doc.toObject(TimerStats::class.java)!!
                dbHelper.updateStats(serverStats)
            }
        } catch (e: Exception) {
            Log.w("TimerRepo", "syncStats failed", e)
        }
    }

    private fun TimerStats.toFirebaseMap() = mapOf(
        "userId" to userId,
        "totalFocusMinutes" to totalFocusMinutes,
        "completedSessions" to completedSessions,
        "currentStreak" to currentStreak,
        "maxStreak" to maxStreak,
        "perfectSessions" to perfectSessions,
        "sessionsWith1Ticket" to sessionsWith1Ticket,
        "sessionsWith2Tickets" to sessionsWith2Tickets,
        "sessionsWith3PlusTickets" to sessionsWith3PlusTickets,
        "lastUpdated" to FieldValue.serverTimestamp()
    )
}