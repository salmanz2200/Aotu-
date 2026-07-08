package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.AutomationTask
import com.example.data.ExecutionLog
import com.example.network.GeminiApiClient
import com.example.utils.AlarmScheduler
import com.example.utils.ShellExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MimicStep(
    val action: String,
    val x: Float,
    val y: Float,
    val delayMs: Long
)

class AutomationService : Service() {
    private val TAG = "AutomationService"
    private val CHANNEL_ID = "AutomatorServiceChannel"
    private val NOTIFICATION_ID = 888

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AutomationService Created")
        createNotificationChannel()
        val notification = createNotification("جاري تهيئة مهمة التشغيل التلقائي...")
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getIntExtra("TASK_ID", -1) ?: -1
        Log.d(TAG, "AutomationService StartCommand for task ID: $taskId")

        if (taskId != -1) {
            try {
                // Acquire wake lock to keep the device awake during active execution
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager == null) {
                    Log.e(TAG, "PowerManager not available, stopping service")
                    cleanupAndStop()
                    return START_NOT_STICKY
                }
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Automator:FullExecutionWakeLock"
                )
                wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes max

                updateNotification("جاري تشغيل المهمة تلقائياً...")
                runTask(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Error during onStartCommand initialization", e)
                cleanupAndStop()
            }
        } else {
            cleanupAndStop()
        }

        return START_NOT_STICKY
    }

    private fun runTask(taskId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(this@AutomationService)
                val task = db.taskDao().getTaskById(taskId)
                
                if (task == null || !task.isEnabled) {
                    Log.w(TAG, "Task $taskId not found or disabled")
                    cleanupAndStop()
                    return@launch
                }

                // Re-acquire wakeLock with dynamic duration based on task validation wait time to prevent sleep
                val durationMs = (task.validationWaitTimeSec.coerceIn(1, 3600) + 120) * 1000L
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.release()
                    }
                } catch (e: Exception) {}

                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager == null) {
                    Log.e(TAG, "PowerManager not available in runTask")
                    cleanupAndStop()
                    return@launch
                }
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Automator:FullExecutionWakeLock"
                )
                wakeLock?.acquire(durationMs)
                Log.d(TAG, "Acquired wakeLock for dynamic duration: ${durationMs / 1000} seconds")

                Log.d(TAG, "Running automation task: ${task.name} for package ${task.packageName}")
                var executionSuccess = false
                var executionMsg = ""

                try {
                    // 1. Wake up device screen
                    ShellExecutor.execute("input keyevent KEYCODE_WAKEUP", useRoot = true)
                    ShellExecutor.execute("input keyevent 224", useRoot = true) // Screen on
                    ShellExecutor.execute("wm dismiss-keyguard", useRoot = true) // Dismiss lockscreen
                    delay(1500)

                    // 2. Perform the execution steps based on user-defined limits
                    val maxLimit = if (task.loopUntilSuccess) task.maxAttempts.coerceAtLeast(1) else 1
                    var attempt = 1
                    while (attempt <= maxLimit && !executionSuccess) {
                        Log.d(TAG, "Attempt $attempt / $maxLimit for task ${task.name}")
                        updateNotification("جاري تنفيذ المحاولة $attempt من $maxLimit...")
                        val runResult = executeTaskSteps(task)
                        executionSuccess = runResult.first
                        executionMsg = runResult.second

                        if (!executionSuccess && attempt < maxLimit) {
                            val retryDelayMs = attempt * 8000L // Progressive backoff: 8s, 16s, 24s...
                            Log.w(TAG, "Attempt $attempt failed: $executionMsg. Retrying in ${retryDelayMs / 1000} seconds...")
                            updateNotification("فشلت المحاولة $attempt. جاري إغلاق التطبيق وإعادة المحاولة بعد ${retryDelayMs / 1000} ثوانٍ...")
                            ShellExecutor.simulateOrExecuteForceStop(task.packageName)
                            try {
                                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                                activityManager.killBackgroundProcesses(task.packageName)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error calling killBackgroundProcesses", e)
                            }
                            delay(retryDelayMs)
                        }
                        attempt++
                    }

                    // 3. Force stop target app to clean up
                    Log.d(TAG, "Force stopping target app: ${task.packageName}")
                    updateNotification("جاري إغلاق التطبيق المستهدف بالكامل من الخلفية...")
                    ShellExecutor.simulateOrExecuteForceStop(task.packageName)
                    try {
                        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        activityManager.killBackgroundProcesses(task.packageName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling killBackgroundProcesses", e)
                    }

                    // Wait 2 seconds to make sure cleanup is fully completed
                    delay(2000)

                    // 4. Lock screen again to preserve battery
                    ShellExecutor.simulateOrExecuteLockScreen()

                } catch (e: Exception) {
                    executionSuccess = false
                    executionMsg = "خطأ غير متوقع: ${e.message}"
                    Log.e(TAG, "Error executing task", e)
                }

                // 5. Update task stats in DB
                val finalStatus = if (executionSuccess) "SUCCESS" else "FAILED"
                val updatedTask = task.copy(
                    lastRunTime = System.currentTimeMillis(),
                    lastRunStatus = finalStatus
                )
                db.taskDao().updateTask(updatedTask)

                // 6. Log the execution
                db.logDao().insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        taskName = task.name,
                        packageName = task.packageName,
                        status = finalStatus,
                        message = executionMsg
                    )
                )

                // 7. Reschedule the task for the next scheduled day if recurring, or disable if single run
                if (updatedTask.isRecurring) {
                    AlarmScheduler.scheduleTask(this@AutomationService, updatedTask)
                } else {
                    val nonRecurringDisabledTask = updatedTask.copy(isEnabled = false)
                    db.taskDao().updateTask(nonRecurringDisabledTask)
                    AlarmScheduler.cancelTask(this@AutomationService, nonRecurringDisabledTask)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Critical outer error in runTask", e)
            } finally {
                cleanupAndStop()
            }
        }
    }

    private suspend fun runActionSteps(task: AutomationTask): Pair<Boolean, String> {
        if (task.actionType == "TAP") {
            Log.d(TAG, "Executing single tap at (${task.tapX}, ${task.tapY})")
            updateNotification("جاري الضغط على الموقع المحدد...")
            val tapRes = ShellExecutor.simulateOrExecuteTap(task.tapX, task.tapY)
            return if (tapRes.isSuccess) {
                Pair(true, "تم الضغط بنجاح على الموقع المحدد (${task.tapX.toInt()}, ${task.tapY.toInt()})")
            } else {
                Pair(false, "فشل محاكاة الضغط: ${tapRes.error}")
            }
        } else {
            // Mimic video sequence
            val steps = parseMimicSteps(task.mimicSteps)
            if (steps.isEmpty()) {
                return Pair(false, "قائمة الحركات المقلدة فارغة")
            }

            Log.d(TAG, "Mimicking video sequence with ${steps.size} steps")
            updateNotification("جاري تقليد حركات الفيديو تلقائياً...")

            for ((index, step) in steps.withIndex()) {
                Log.d(TAG, "Executing step ${index + 1}/${steps.size}: Tap (${step.x}, ${step.y}) after ${step.delayMs}ms")
                delay(step.delayMs.coerceAtLeast(100L))
                ShellExecutor.simulateOrExecuteTap(step.x, step.y)
            }
            return Pair(true, "تم تنفيذ جميع حركات الفيديو التلقائية بنجاح")
        }
    }

    private suspend fun performValidationSteps(task: AutomationTask, defaultMsg: String): Pair<Boolean, String> {
        val validationType = task.validationType
        if (validationType == "TEXT") {
            val validationText = task.validationText
            if (validationText.isNullOrBlank()) {
                return Pair(true, "$defaultMsg (تم النجاح افتراضياً لعدم تحديد نص التحقق)")
            }

            Log.d(TAG, "Validating via screen text: '$validationText'")
            updateNotification("جاري التحقق من النص: '$validationText' على الشاشة...")
            val actualCapPath = File(cacheDir, "actual_cap_${task.id}.png").absolutePath
            val capRes = ShellExecutor.captureScreenshot(actualCapPath)
            
            if (capRes && File(actualCapPath).exists()) {
                val actualBitmap = BitmapFactory.decodeFile(actualCapPath)
                if (actualBitmap != null) {
                    val containsText = GeminiApiClient.verifyWithGemini(actualBitmap, null, validationText)
                    Log.d(TAG, "Text verification outcome: $containsText")
                    return when (containsText) {
                        true -> Pair(true, "نجح")
                        false -> Pair(false, "فشل: لم يتم العثور على النص المطلوب '$validationText' على الشاشة")
                        null -> Pair(false, "فشل: انتهت مهلة التحقق أو حدث خطأ أثناء الاتصال بخدمة Gemini")
                    }
                }
            }
            return Pair(false, "فشل: تعذر التقاط صورة للشاشة للتحقق من النص")

        } else if (validationType == "IMAGE") {
            // Use user-provided reference image path if available, otherwise fallback to targetLastFrame
            val appDir = getExternalFilesDir(null) ?: filesDir
            val customRefFile = task.referenceImagePath?.let { File(it) }
            val targetLastFrameFile = File(appDir, "target_last_frame_${task.id}.jpg")

            val referenceFile = when {
                customRefFile != null && customRefFile.exists() -> customRefFile
                targetLastFrameFile.exists() -> targetLastFrameFile
                else -> null
            }

            if (referenceFile != null) {
                Log.d(TAG, "Validating via image comparison with file: ${referenceFile.absolutePath}. Taking screen capture...")
                updateNotification("جاري التحقق من تطابق الشاشة مع الصورة المرجعية...")
                val actualCapPath = File(cacheDir, "actual_cap_${task.id}.png").absolutePath
                val capRes = ShellExecutor.captureScreenshot(actualCapPath)
                
                if (capRes && File(actualCapPath).exists()) {
                    val targetBitmap = BitmapFactory.decodeFile(referenceFile.absolutePath)
                    val actualBitmap = BitmapFactory.decodeFile(actualCapPath)
                    
                    if (targetBitmap != null && actualBitmap != null) {
                        val matchResult = GeminiApiClient.verifyWithGemini(actualBitmap, targetBitmap, null)
                        Log.d(TAG, "Image verification outcome: $matchResult")
                        return when (matchResult) {
                            true -> Pair(true, "نجح")
                            false -> Pair(false, "فشل: الشاشة الحالية لا تتطابق مع صورة النجاح المرجعية")
                            null -> Pair(false, "فشل: انتهت مهلة التحقق أو حدث خطأ أثناء الاتصال بخدمة Gemini")
                        }
                    } else {
                        return Pair(false, "فشل: تعذر تحميل الصور للتحقق من التطابق")
                    }
                }
                return Pair(false, "فشل: تعذر التقاط صورة للشاشة للمقارنة بالصورة المرجعية")
            } else {
                return Pair(true, "$defaultMsg (تم النجاح لعدم العثور على صورة مرجعية للتحقق)")
            }
        }

        // validationType == "NONE"
        return Pair(true, defaultMsg)
    }

    private suspend fun executeTaskSteps(task: AutomationTask): Pair<Boolean, String> {
        // Launch target app
        val launchIntent = packageManager.getLaunchIntentForPackage(task.packageName)
        if (launchIntent == null) {
            return Pair(false, "فشل: لم يتم العثور على التطبيق المستهدف أو تعذر تشغيله")
        }
        
        // Use am start via Root shell to launch the activity with full system privileges
        var amStarted = false
        val componentName = launchIntent.component
        if (componentName != null) {
            val componentStr = "${componentName.packageName}/${componentName.className}"
            Log.d(TAG, "Launching via am start shell command: $componentStr")
            val shellResult = ShellExecutor.execute("am start -n $componentStr", useRoot = true)
            if (shellResult.isSuccess) {
                amStarted = true
                Log.d(TAG, "Successfully launched app via am start shell.")
            }
        }
        
        // Fallback to standard context launch if am start didn't succeed or root wasn't available
        if (!amStarted) {
            Log.d(TAG, "Falling back to standard startActivity launch...")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)
        }

        updateNotification("جاري فتح التطبيق ${task.appName} والانتظار حتى يصبح جاهزاً...")
        ShellExecutor.waitForAppToBeReady(this, task.packageName, 10)

        val delaySec = task.delayBeforeTapSec.coerceIn(1, 300)
        Log.d(TAG, "الانتظار الإضافي $delaySec ثوانٍ للتحميل الكامل قبل الضغط...")
        updateNotification("الانتظار $delaySec ثوانٍ لإتاحة تحميل محتويات الصفحة...")
        
        delay(delaySec * 1000L)

        // 1. Run action steps
        val actionResult = runActionSteps(task)
        val lastExecutionMsg = actionResult.second
        if (!actionResult.first) {
            return Pair(false, "فشل أثناء تنفيذ الخطوات: $lastExecutionMsg")
        }

        // 2. Wait custom validation wait time (انتظر الوقت المحدد من المستخدم)
        val waitTimeSec = task.validationWaitTimeSec.coerceIn(1, 3600)
        Log.d(TAG, "Waiting $waitTimeSec seconds before performing success verification...")
        updateNotification("جاري الانتظار $waitTimeSec ثانية للتأكد من اكتمال العملية...")
        
        delay(waitTimeSec * 1000L)

        // 3. Perform validation (التقط صورة للشاشة والتحقق مع Gemini)
        val validationResult = performValidationSteps(task, lastExecutionMsg)
        return validationResult
    }


    private fun parseMimicSteps(json: String?): List<MimicStep> {
        if (json.isNullOrEmpty()) return emptyList()
        val steps = mutableListOf<MimicStep>()
        try {
            // Standard JSON parser is extremely robust compared to Regex!
            // First clean up any potential markdown wraps
            var cleanJson = json.trim()
            if (cleanJson.startsWith("```")) {
                val lines = cleanJson.lines()
                if (lines.size >= 2) {
                    val contentLines = lines.subList(1, lines.size - 1)
                    cleanJson = contentLines.joinToString("\n").trim()
                }
            }
            if (cleanJson.startsWith("json")) {
                cleanJson = cleanJson.substring(4).trim()
            }
            
            val jsonArray = org.json.JSONArray(cleanJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val x = obj.optDouble("x", 0.0).toFloat()
                val y = obj.optDouble("y", 0.0).toFloat()
                val delayMs = obj.optLong("delayMs", 1000L)
                val action = obj.optString("action", "tap")
                steps.add(MimicStep(action, x, y, delayMs))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON steps standardly, falling back to regex", e)
            try {
                val matches = Regex("""\{\s*"action"\s*:\s*"[^"]*"\s*,\s*"x"\s*:\s*([0-9.]+)\s*,\s*"y"\s*:\s*([0-9.]+)\s*,\s*"delayMs"\s*:\s*([0-9]+)\}""").findAll(json)
                for (match in matches) {
                    val x = match.groupValues[1].toFloatOrNull() ?: 0f
                    val y = match.groupValues[2].toFloatOrNull() ?: 0f
                    val delay = match.groupValues[3].toLongOrNull() ?: 1000L
                    steps.add(MimicStep("tap", x, y, delay))
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Regex fallback also failed", e2)
            }
        }
        return steps
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released successfully.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakeLock", e)
        } finally {
            wakeLock = null
        }
    }

    private fun cleanupAndStop() {
        releaseWakeLock()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        Log.d(TAG, "AutomationService Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // --- Notification Helpers ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "قناة تشغيل المهام التلقائية",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("أوتوماتور - تشغيل تلقائي")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }
}
