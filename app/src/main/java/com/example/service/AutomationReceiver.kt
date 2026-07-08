package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutomationReceiver : BroadcastReceiver() {
    private val TAG = "AutomationReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast action: $action")

        if (action == "com.example.ACTION_RUN_TASK") {
            val taskId = intent.getIntExtra("TASK_ID", -1)
            Log.d(TAG, "Received alarm to run task ID: $taskId")
            if (taskId != -1) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Automator:ExecutionWakeLock"
                )

                // goAsync() tells the system that onReceive() is not finished yet, preventing
                // the process from being killed before the coroutine completes its work.
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        wakeLock?.acquire(10000)
                        Log.d(TAG, "WakeLock acquired")

                        val serviceIntent = Intent(context, AutomationService::class.java).apply {
                            putExtra("TASK_ID", taskId)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting AutomationService from receiver", e)
                    } finally {
                        try {
                            if (wakeLock != null && wakeLock.isHeld) {
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
    }
}
