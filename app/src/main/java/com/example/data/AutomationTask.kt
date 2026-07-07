package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_tasks")
data class AutomationTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val packageName: String,
    val appName: String,
    val timeHour: Int,
    val timeMinute: Int,
    val daysOfWeek: String, // Comma separated days e.g. "1,2,3,4,5,6,7" (1=Mon, 7=Sun)
    val isEnabled: Boolean = true,
    val actionType: String = "TAP", // "TAP" or "MIMIC_VIDEO"
    val tapX: Float = 0f,
    val tapY: Float = 0f,
    val videoFilePath: String? = null,
    val mimicSteps: String? = null, // JSON string representing list of clicks/delays
    val lastRunTime: Long = 0L,
    val lastRunStatus: String? = null, // "SUCCESS", "FAILED"
    val validationType: String = "NONE", // "NONE", "TEXT", "IMAGE"
    val validationText: String? = null, // Text to search for on screen to verify success
    val validationWaitTimeSec: Int = 6, // Wait time in seconds (1s to 3600s)
    val loopUntilSuccess: Boolean = false, // Follow up until success
    val maxAttempts: Int = 3, // Max attempts for looping
    val referenceImagePath: String? = null, // Saved path of custom reference image for image comparison
    val delayBeforeTapSec: Int = 3 // Wait time before tapping in seconds
)
