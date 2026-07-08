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
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.sec.android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        Log.d(TAG, "Boot completed — rescheduling enabled tasks")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val enabledTasks = db.taskDao().getEnabledTasks()
                Log.d(TAG, "Rescheduling ${enabledTasks.size} enabled tasks after boot")
                enabledTasks.forEach { task ->
                    AlarmScheduler.scheduleTask(context, task)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling tasks after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
