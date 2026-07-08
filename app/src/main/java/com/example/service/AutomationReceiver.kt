package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.data.AppDatabase
import com.example.utils.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutomationReceiver : BroadcastReceiver() {
    private val TAG = "AutomationReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast action: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager == null) {
            Log.e(TAG, "PowerManager not available")
            return
        }

        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Automator:ExecutionWakeLock"
        )

        // goAsync() tells the system that onReceive() is not finished yet, preventing
        // the process from being killed before the coroutine completes its work.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                wakeLock.acquire(10000)
                Log.d(TAG, "WakeLock acquired")

                if (action == Intent.ACTION_BOOT_COMPLETED ||
                    action == "android.intent.action.QUICKBOOT_POWERON" ||
                    action == "com.sec.android.intent.action.QUICKBOOT_POWERON"
                ) {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val enabledTasks = db.taskDao().getEnabledTasks()
                        Log.d(TAG, "Re-scheduling ${enabledTasks.size} enabled tasks after boot")
                        enabledTasks.forEach { task ->
                            AlarmScheduler.scheduleTask(context, task)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error rescheduling tasks after boot", e)
                    }
                } else if (action == "com.example.ACTION_RUN_TASK") {
                    val taskId = intent.getIntExtra("TASK_ID", -1)
                    Log.d(TAG, "Received alarm to run task ID: $taskId")
                    if (taskId != -1) {
                        val serviceIntent = Intent(context, AutomationService::class.java).apply {
                            putExtra("TASK_ID", taskId)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }

                        try {
                            val db = AppDatabase.getDatabase(context)
                            val task = db.taskDao().getTaskById(taskId)
                            if (task != null && task.isEnabled) {
                                if (task.isRecurring) {
                                    Log.d(TAG, "Rescheduling recurring task ID: $taskId")
                                    AlarmScheduler.scheduleTask(context, task)
                                } else {
                                    Log.d(TAG, "Disabling non-recurring task ID: $taskId after execution start")
                                    val disabledTask = task.copy(isEnabled = false)
                                    db.taskDao().updateTask(disabledTask)
                                    AlarmScheduler.cancelTask(context, disabledTask)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error rescheduling task ID $taskId in receiver", e)
                        }
                    }
                }
            } finally {
                try {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                        Log.d(TAG, "WakeLock released")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing WakeLock", e)
                }
                // Signal to the system that async work is complete
                pendingResult.finish()
            }
        }
    }
}
