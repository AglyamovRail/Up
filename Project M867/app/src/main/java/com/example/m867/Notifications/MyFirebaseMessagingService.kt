package com.example.m867

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.m867.MainActivity
import com.example.m867.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.d("FCM_DEBUG", "Refreshed token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM_DEBUG", "From: ${remoteMessage.from}")

        remoteMessage.data.let { data ->
            Log.d("FCM_DEBUG", "Message data: $data")
        }

        remoteMessage.notification?.let { notification ->
            Log.d("FCM_DEBUG", "Notification Title: ${notification.title}")
            Log.d("FCM_DEBUG", "Notification Body: ${notification.body}")
            sendNotification(this, notification.title, notification.body)
        }
    }

    companion object {
        fun sendNotification(context: Context, title: String?, messageBody: String?) {
            Log.d("FCM_DEBUG", "Building notification...")

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val channelId = "default_channel"
            val notificationBuilder = NotificationCompat.Builder(context, channelId).apply {
                setSmallIcon(R.drawable.ic_notification)
                setContentTitle(title ?: "Уведомление")
                setContentText(messageBody ?: "Новое сообщение")
                priority = NotificationCompat.PRIORITY_HIGH
                setAutoCancel(true)
                setContentIntent(pendingIntent)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Основные уведомления",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Канал для основных уведомлений"
                }
                notificationManager.createNotificationChannel(channel)
            }

            notificationManager.notify(
                System.currentTimeMillis().toInt(),
                notificationBuilder.build()
            )
        }
    }
}