package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AutomationTask
import com.example.service.AutomationReceiver
import java.util.*

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun scheduleTask(context: Context, task: AutomationTask) {
        if (!task.isEnabled) {
            cancelTask(context, task)
            return
        }

        val nextTriggerTime = calculateNextTriggerTime(task.timeHour, task.timeMinute, task.daysOfWeek)
        if (nextTriggerTime == null) {
            Log.w(TAG, "No valid trigger days for task ${task.id}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutomationReceiver::class.java).apply {
            action = "com.example.ACTION_RUN_TASK"
            putExtra("TASK_ID", task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(TAG, "Scheduling task ${task.id} (${task.name}) at ${Date(nextTriggerTime)}")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = PendingIntent.getActivity(
                    context,
                    task.id,
                    Intent(context, com.example.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val info = AlarmManager.AlarmClockInfo(nextTriggerTime, showIntent)
                alarmManager.setAlarmClock(info, pendingIntent)
                Log.d(TAG, "Scheduled exact alarm using setAlarmClock for task ${task.id} at ${Date(nextTriggerTime)}")
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm, falling back to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerTime,
                pendingIntent
            )
        }
    }

    fun cancelTask(context: Context, task: AutomationTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutomationReceiver::class.java).apply {
            action = "com.example.ACTION_RUN_TASK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            Log.d(TAG, "Cancelling scheduled alarm for task ${task.id}")
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun calculateNextTriggerTime(hour: Int, minute: Int, daysOfWeekStr: String): Long? {
        val daysList = daysOfWeekStr.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .sorted()

        if (daysList.isEmpty()) return null

        val now = Calendar.getInstance()
        val currentDayOfWeekAndroid = now.get(Calendar.DAY_OF_WEEK) // Sunday=1, Monday=2, ..., Saturday=7
        // Convert to Mon=1, Tue=2, ..., Sun=7 standard
        val currentDay = when (currentDayOfWeekAndroid) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        var nextDayToTrigger = -1
        var daysOffset = 0

        // Find standard offset
        for (day in daysList) {
            if (day > currentDay) {
                nextDayToTrigger = day
                daysOffset = day - currentDay
                break
            } else if (day == currentDay) {
                // Check if hour:minute is in the future today
                val todayTrigger = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val nowCompare = (now.clone() as Calendar).apply {
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (todayTrigger.timeInMillis <= nowCompare.timeInMillis) {
                    // Past time today, skip scheduling for today
                } else {
                    // Future time today, schedule for today
                    nextDayToTrigger = day
                    daysOffset = 0
                    break
                }
            }
        }

        // If no day found in remainder of week, pick the first day next week
        if (nextDayToTrigger == -1) {
            val firstDay = daysList.first()
            nextDayToTrigger = firstDay
            daysOffset = (7 - currentDay) + firstDay
        }

        val triggerCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, daysOffset)
        }

        return triggerCal.timeInMillis
    }
}
