package com.example.m867.Timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.m867.Timer.PomodoroService

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val serviceIntent = Intent(context, PomodoroService::class.java)
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("TimerReceiver", "Error starting service", e)
        }
    }
}