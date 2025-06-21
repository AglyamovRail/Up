package com.example.m867.Task

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import com.example.m867.NetworkUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class TaskRepository(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val tasksCollection = db.collection("tasks")
    private val dbHelper = TaskDbHelper(context)

    // Добавление задачи
    suspend fun addTask(task: Task): String {
        return try {
            // Генерируем ID сразу как Firebase-совместимый
            val newId = tasksCollection.document().id
            val taskWithId = task.copy(id = newId, isSynced = false)

            // Сохраняем локально
            dbHelper.addTask(taskWithId)

            // Пытаемся сразу синхронизировать
            if (NetworkUtils.isNetworkAvailable(context)) {
                try {
                    tasksCollection.document(newId)
                        .set(taskWithId.toFirebaseMap())
                        .await()
                    dbHelper.updateTask(taskWithId.copy(isSynced = true))
                } catch (e: Exception) {
                    Log.w("TaskRepository", "Failed to sync new task", e)
                }
            }
            newId
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error adding task", e)
            throw e
        }
    }

    // Обновление задачи
    suspend fun updateTask(task: Task) {
        try {
            // Обновляем локально и помечаем как несинхронизированную
            dbHelper.updateTask(task.copy(isSynced = false))

            // Пытаемся синхронизировать, если есть интернет
            if (NetworkUtils.isNetworkAvailable(context)) {
                try {
                    tasksCollection.document(task.id)
                        .update(task.toUpdateMap())
                        .await()
                    // Помечаем как синхронизированную
                    dbHelper.updateTask(task.copy(isSynced = true))
                } catch (e: Exception) {
                    Log.w("TaskRepository", "Failed to sync task update", e)
                }
            }
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error updating task", e)
            throw e
        }
    }

    // Удаление задачи (мягкое удаление)
    suspend fun deleteTask(taskId: String) {
        try {
            // Локально помечаем как удаленную
            val task = dbHelper.getTaskById(taskId)?.copy(
                isDeleted = true,
                isSynced = false
            )
            task?.let { dbHelper.updateTask(it) }

        } catch (e: Exception) {
            Log.e("TaskRepository", "Error deleting task", e)
            throw e
        }
    }

    suspend fun getCompletedTasksForStats(userId: String): List<Task> {
        return try {
            // Берем данные напрямую из Firestore
            tasksCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isCompleted", true)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.getTimestamp("completedAt")?.let { completedAt ->
                        Task(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            priority = doc.getLong("priority")?.toInt() ?: 1,
                            userId = doc.getString("userId") ?: "",
                            createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                            isCompleted = true,
                            completedAt = completedAt,
                            updatedAt = doc.getTimestamp("updatedAt") ?: Timestamp.now()
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error loading stats", e)
            emptyList()
        }
    }

    // Получение задач (всегда из локальной БД)
    suspend fun getTasks(userId: String): List<Task> {
        return try {
            dbHelper.getAllTasks(userId)
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error getting tasks", e)
            emptyList()
        }
    }

    // Получение задач, отсортированных по дате (сначала новые)
    suspend fun getTasksSortedByDateAsc(userId: String): List<Task> {
        return try {
            dbHelper.getAllTasks(userId).sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error getting sorted tasks", e)
            emptyList()
        }
    }

    // Получение задач, отсортированных по приоритету
    suspend fun getTasksSortedByPriority(userId: String): List<Task> {
        return try {
            dbHelper.getAllTasks(userId).sortedWith(
                compareBy<Task> { it.priority }
                    .thenByDescending { it.createdAt }
            )
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error getting priority sorted tasks", e)
            emptyList()
        }
    }

    // Переключение статуса выполнения задачи
    suspend fun toggleTaskCompletion(taskId: String, isCompleted: Boolean) {
        try {
            val task = dbHelper.getTaskById(taskId)?.copy(
                isCompleted = isCompleted,
                completedAt = if (isCompleted) Timestamp.now() else null,
                isSynced = false,
                updatedAt = Timestamp.now()
            )
            task?.let { dbHelper.updateTask(it) }
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error toggling task completion", e)
            throw e
        }
    }

    suspend fun getCompletedTasksFromFirebase(userId: String): List<Task> {
        return try {
            tasksCollection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isCompleted", true)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.getTimestamp("completedAt")?.let { completedAt ->
                        Task(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            priority = doc.getLong("priority")?.toInt() ?: 1,
                            userId = doc.getString("userId") ?: "",
                            createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                            isCompleted = true,
                            completedAt = completedAt,
                            updatedAt = doc.getTimestamp("updatedAt") ?: Timestamp.now()
                        )
                    }
                }
        } catch (e: Exception) {
            emptyList()
        }
    }


    suspend fun getCompletedTasksHistory(userId: String): List<Task> {
        return dbHelper.getAllTasksIncludeDeleted(userId)
            .filter { it.isCompleted && it.completedAt != null }
    }




    // Синхронизация данных с Firebase
    suspend fun syncTasks(userId: String) {
        if (!NetworkUtils.isNetworkAvailable(context)) return

        try {
            // 1. Отправляем все локальные изменения на сервер
            val localTasks = dbHelper.getAllTasksIncludeDeleted(userId)

            // Отправляем все задачи, которые:
            // - Не удалены ИЛИ
            // - Были изменены локально (isSynced = false)
            localTasks.forEach { task ->
                try {
                    if (!task.isDeleted || !task.isSynced) {
                        // Для новых задач используем set()
                        if (!task.isSynced) {
                            tasksCollection.document(task.id)
                                .set(task.toFirebaseMap())
                                .await()
                        }
                        // Для обновлений используем update()
                        else {
                            tasksCollection.document(task.id)
                                .update(task.toUpdateMap())
                                .await()
                        }

                        // Помечаем как синхронизированную
                        dbHelper.updateTask(task.copy(isSynced = true))
                    }
                } catch (e: Exception) {
                    Log.w("TaskRepository", "Sync error for task ${task.id}", e)
                }
            }

            // 2. Получаем все задачи с сервера
            val serverTasks = tasksCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    try {
                        Task(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            priority = doc.getLong("priority")?.toInt() ?: 1,
                            userId = doc.getString("userId") ?: "",
                            createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                            isCompleted = doc.getBoolean("isCompleted") ?: false,
                            completedAt = doc.getTimestamp("completedAt"),
                            updatedAt = doc.getTimestamp("updatedAt") ?: Timestamp.now(),
                            isSynced = true
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

            // 3. Обновляем локальную БД
            serverTasks.forEach { serverTask ->
                val localTask = dbHelper.getTaskById(serverTask.id)
                // Добавляем только если задачи нет локально ИЛИ серверная версия новее
                if (localTask == null ||
                    (serverTask.updatedAt.seconds > localTask.updatedAt.seconds)) {
                    dbHelper.addOrUpdateTask(serverTask)
                }
            }
        } catch (e: Exception) {
            Log.e("TaskRepository", "Sync failed", e)
            throw e
        }
    }

    // Вспомогательные методы преобразования в Map для Firebase
    private fun Task.toFirebaseMap(): Map<String, Any> = hashMapOf(
        "title" to title,
        "priority" to priority,
        "userId" to userId,
        "createdAt" to createdAt,
        "isCompleted" to isCompleted,
        "updatedAt" to FieldValue.serverTimestamp(),
        "lastLocalUpdate" to Timestamp.now().seconds,
        "completedAt" to (completedAt ?: FieldValue.delete()),
        "isSynced" to true,
        "isDeleted" to isDeleted
    )

    private fun Task.toUpdateMap(): Map<String, Any> = hashMapOf(
        "title" to title,
        "priority" to priority,
        "isCompleted" to isCompleted,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    suspend fun getCompletedTasksByDate(userId: String): Map<String, List<Task>> {
        return dbHelper.getAllTasks(userId)
            .filter { it.isCompleted }
            .groupBy { task ->
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(task.createdAt.toDate())
            }
    }
}