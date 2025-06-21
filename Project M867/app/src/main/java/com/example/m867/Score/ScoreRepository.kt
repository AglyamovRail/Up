package com.example.m867.Score

import android.content.Context
import android.util.Log
import com.example.m867.NetworkUtils
import com.example.m867.Score.ScoreDbHelper
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class ScoreRepository(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val dbHelper = ScoreDbHelper(context)

    suspend fun addPoints(userId: String, pointsToAdd: Int) {
        val current = dbHelper.getScore(userId) ?: UserScore(userId)
        current.points += pointsToAdd
        current.lastUpdated = Timestamp.Companion.now()
        dbHelper.upsertScore(current)

        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                db.collection("user_scores").document(userId)
                    .set(current.toFirebaseMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("ScoreRepo", "Sync error", e)
            }
        }
    }

    suspend fun syncScores(userId: String) {
        if (!NetworkUtils.isNetworkAvailable(context)) return

        dbHelper.getScore(userId)?.let { localScore ->
            try {
                db.collection("user_scores").document(userId)
                    .set(localScore.toFirebaseMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("ScoreRepo", "Sync error", e)
            }
        }

        try {
            val serverScore = db.collection("user_scores").document(userId).get().await()
            serverScore.toObject(UserScore::class.java)?.let { remote ->
                val local = dbHelper.getScore(userId) ?: UserScore(userId)
                if (remote.lastUpdated > local.lastUpdated) {
                    dbHelper.upsertScore(remote)
                }
            }
        } catch (e: Exception) {
            Log.e("ScoreRepo", "Download error", e)
        }
    }

    fun getCurrentScore(userId: String): Int {
        return dbHelper.getScore(userId)?.points ?: 0
    }

    private fun UserScore.toFirebaseMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "points" to points,
        "lastUpdated" to lastUpdated
    )

    // Метод для изменения очков
    suspend fun updatePoints(userId: String, delta: Int) {
        val current = dbHelper.getScore(userId) ?: UserScore(userId, 0)
        val newPoints = (current.points + delta).coerceAtLeast(0)
        val updated = current.copy(points = newPoints)

        dbHelper.upsertScore(updated)

        if (NetworkUtils.isNetworkAvailable(context)) {
            try {
                db.collection("user_scores").document(userId)
                    .set(mapOf(
                        "points" to FieldValue.increment(delta.toLong()),
                        "lastUpdated" to FieldValue.serverTimestamp()
                    ), SetOptions.merge())
            } catch (e: Exception) {
                Log.e("ScoreRepo", "Update error", e)
            }
        }
    }
}