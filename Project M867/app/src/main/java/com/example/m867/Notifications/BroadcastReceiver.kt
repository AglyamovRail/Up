package com.example.m867
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Напоминание"
        val body = intent.getStringExtra("body") ?: "Не забудьте выполнить задачи!"

        MyFirebaseMessagingService.sendNotification(context, title, body)
    }
}