package com.example.m867.Task

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.firebase.Timestamp

class TaskDbHelper(context: Context) : SQLiteOpenHelper(context, "tasks.db", null, 5) {

    override fun onCreate(db: SQLiteDatabase) {
        val query = """
            CREATE TABLE tasks (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                priority INTEGER NOT NULL,
                userId TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                isCompleted INTEGER NOT NULL,
                isSynced INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                completedAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(query)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER DEFAULT 0")
        }
    }

    fun addTask(task: Task) {
        val values = ContentValues().apply {
            put("id", task.id)
            put("title", task.title)
            put("priority", task.priority)
            put("userId", task.userId)
            put("createdAt", task.createdAt.seconds)
            put("isCompleted", if (task.isCompleted) 1 else 0)
            put("isSynced", if (task.isSynced) 1 else 0)
            put("isDeleted", if (task.isDeleted) 1 else 0)
            put("completedAt", task.completedAt?.seconds ?: 0)
            put("completedAt", task.completedAt?.seconds ?: 0)
        }
        writableDatabase.insert("tasks", null, values)
    }

    fun updateTask(task: Task) {
        val values = ContentValues().apply {
            put("title", task.title)
            put("priority", task.priority)
            put("isCompleted", if (task.isCompleted) 1 else 0)
            put("isSynced", if (task.isSynced) 1 else 0)
            put("isDeleted", if (task.isDeleted) 1 else 0)
            put("completedAt", task.completedAt?.seconds ?: 0)
            put("completedAt", task.completedAt?.seconds ?: 0)
        }
        writableDatabase.update("tasks", values, "id = ?", arrayOf(task.id))
    }

    fun deleteTask(id: String) {
        writableDatabase.delete("tasks", "id = ?", arrayOf(id))
    }

    fun getAllTasksIncludeDeleted(userId: String): List<Task> {
        val tasks = mutableListOf<Task>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE userId = ?",
            arrayOf(userId)
        )

        if (cursor.moveToFirst()) {
            do {
                tasks.add(Task(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow("userId")),
                    createdAt = Timestamp(cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")), 0),
                    isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("isCompleted")) == 1,
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow("isSynced")) == 1,
                    isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")) == 1,
                    completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completedAt")).let {
                        if (it > 0) Timestamp(it, 0) else null
                    },
                    updatedAt = Timestamp.now()
                ))
            } while (cursor.moveToNext())
        }

        cursor.close()
        return tasks
    }
    fun getAllTasks(userId: String): List<Task> {
        val tasks = mutableListOf<Task>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE userId = ? AND isDeleted = 0",
            arrayOf(userId)
        )

        if (cursor.moveToFirst()) {
            do {
                tasks.add(Task(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow("userId")),
                    createdAt = Timestamp(cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")), 0),
                    isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("isCompleted")) == 1,
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow("isSynced")) == 1,
                    isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")) == 1,
                    updatedAt = Timestamp.now()
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return tasks
    }



    fun getTaskById(taskId: String): Task? {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE id = ?",
            arrayOf(taskId)
        )
        return if (cursor.moveToFirst()) {
            Task(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
                userId = cursor.getString(cursor.getColumnIndexOrThrow("userId")),
                createdAt = Timestamp(cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")), 0),
                isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("isCompleted")) == 1,
                isSynced = cursor.getInt(cursor.getColumnIndexOrThrow("isSynced")) == 1,
                isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")) == 1,
                updatedAt = Timestamp.now()
            )
        } else {
            null
        }.also { cursor.close() }
    }

    fun getUnsyncedTasks(userId: String): List<Task> {
        val tasks = mutableListOf<Task>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE userId = ? AND isSynced = 0",
            arrayOf(userId)
        )

        if (cursor.moveToFirst()) {
            do {
                tasks.add(Task(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow("userId")),
                    createdAt = Timestamp(cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")), 0),
                    isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("isCompleted")) == 1,
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow("isSynced")) == 1,
                    isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")) == 1,
                    updatedAt = Timestamp.now()
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return tasks
    }

    fun addOrUpdateTask(task: Task) {
        if (getTaskById(task.id) == null) {
            addTask(task)
        } else {
            updateTask(task)
        }
    }

    fun markTaskAsSynced(taskId: String) {
        val values = ContentValues().apply {
            put("isSynced", 1)
        }
        writableDatabase.update("tasks", values, "id = ?", arrayOf(taskId))
    }

    fun isTaskMarkedAsDeleted(taskId: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT isDeleted FROM tasks WHERE id = ?",
            arrayOf(taskId)
        )
        return if (cursor.moveToFirst()) {
            cursor.getInt(0) == 1
        } else {
            false
        }.also { cursor.close() }
    }
}