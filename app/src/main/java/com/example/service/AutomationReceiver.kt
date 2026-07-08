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
        if (intent.action != "com.example.ACTION_RUN_TASK") return

        val taskId = intent.getIntExtra("TASK_ID", -1)
        Log.d(TAG, "Received alarm to run task ID: $taskId")
        if (taskId == -1) return

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager == null) {
            Log.e(TAG, "PowerManager not available")
            return
        }

        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Automator:ExecutionWakeLock"
        )

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                wakeLock.acquire(10_000L)
                Log.d(TAG, "WakeLock acquired")

                val serviceIntent = Intent(context, AutomationService::class.java).apply {
                    putExtra("TASK_ID", taskId)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // Reschedule or disable the task
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
            } finally {
                try {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                        Log.d(TAG, "WakeLock released")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing WakeLock", e)
                }
                pendingResult.finish()
            }
        }
    }
}
