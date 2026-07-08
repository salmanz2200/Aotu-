package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.utils.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "🔔 Boot receiver received: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" || 
            action == "com.sec.android.intent.action.QUICKBOOT_POWERON") {
            
            // Use goAsync to process boot rescheduling on a background thread safely
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val enabledTasks = db.taskDao().getEnabledTasks()
                    Log.d(TAG, "📋 Re-scheduling ${enabledTasks.size} enabled tasks after boot")
                    enabledTasks.forEach { task ->
                        AlarmScheduler.scheduleTask(context, task)
                    }
                    Log.d(TAG, "✅ All boot tasks rescheduled successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error rescheduling tasks after boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
