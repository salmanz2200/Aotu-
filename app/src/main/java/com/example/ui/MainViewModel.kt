package com.example.ui

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.AutomationTask
import com.example.data.ExecutionLog
import com.example.data.TaskRepository
import com.example.network.GeminiApiClient
import com.example.service.AutomationService
import com.example.service.OverlayService
import com.example.utils.AlarmScheduler
import com.example.utils.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class InstalledApp(
    val packageName: String,
    val appLabel: String,
    val icon: Bitmap?
)

data class LocalVideo(
    val uri: Uri,
    val name: String,
    val path: String,
    val durationMs: Long,
    val size: Long,
    val mimeType: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val context = application.applicationContext
    private val repository: TaskRepository

    // Central state Flows
    val allTasks: StateFlow<List<AutomationTask>>
    val allLogs: StateFlow<List<ExecutionLog>>

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _currentScreen = MutableStateFlow("TASKS") // "TASKS", "LOGS", "ADD_TASK", "SETTINGS"
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _isGeminiConfigured = MutableStateFlow(false)
    val isGeminiConfigured: StateFlow<Boolean> = _isGeminiConfigured.asStateFlow()

    private val _videoAnalysisProgress = MutableStateFlow<String?>(null)
    val videoAnalysisProgress: StateFlow<String?> = _videoAnalysisProgress.asStateFlow()

    // For editing or adding new tasks
    private val _editingTask = MutableStateFlow<AutomationTask?>(null)
    val editingTask: StateFlow<AutomationTask?> = _editingTask.asStateFlow()

    private val _localVideos = MutableStateFlow<List<LocalVideo>>(emptyList())
    val localVideos: StateFlow<List<LocalVideo>> = _localVideos.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(context)
        repository = TaskRepository(db.taskDao(), db.logDao())

        allTasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allLogs = repository.allLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        checkRootAndApiKey()
        loadInstalledApps()
        scanLocalVideos()
    }

    fun scanLocalVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val videos = mutableListOf<LocalVideo>()
            
            // 1. Scan public directories recursively
            val directoriesToScan = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                File("/sdcard/Download"),
                File("/sdcard/Movies"),
                File("/sdcard/DCIM")
            )
            
            val visitedPaths = mutableSetOf<String>()
            val supportedExtensions = setOf("mp4", "mov", "webm")
            
            fun scanDirectory(dir: File, depth: Int) {
                if (depth > 4 || !dir.exists() || !dir.isDirectory) return
                val canonicalPath = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
                if (visitedPaths.contains(canonicalPath)) return
                visitedPaths.add(canonicalPath)
                
                val files = dir.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory) {
                        scanDirectory(file, depth + 1)
                    } else if (file.isFile) {
                        val ext = file.extension.lowercase(Locale.US)
                        if (supportedExtensions.contains(ext)) {
                            try {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(file.absolutePath)
                                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                val durationMs = durationStr?.toLongOrNull() ?: 0L
                                retriever.release()
                                
                                val uri = Uri.fromFile(file)
                                videos.add(
                                    LocalVideo(
                                        uri = uri,
                                        name = file.name,
                                        path = file.absolutePath,
                                        durationMs = durationMs,
                                        size = file.length(),
                                        mimeType = "video/$ext"
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "Error reading metadata for ${file.absolutePath}", e)
                                // Still add with 0 duration if metadata retrieval fails
                                val uri = Uri.fromFile(file)
                                videos.add(
                                    LocalVideo(
                                        uri = uri,
                                        name = file.name,
                                        path = file.absolutePath,
                                        durationMs = 0L,
                                        size = file.length(),
                                        mimeType = "video/$ext"
                                    )
                                )
                            }
                        }
                    }
                }
            }
            
            for (dir in directoriesToScan) {
                scanDirectory(dir, 0)
            }
            
            // 2. Also query MediaStore as fallback/supplement
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.MIME_TYPE
                )
                val cursor = context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    
                    while (c.moveToNext()) {
                        val path = c.getString(dataCol)
                        if (videos.any { it.path == path }) continue // Avoid duplicates
                        
                        val name = c.getString(nameCol) ?: File(path).name
                        val duration = c.getLong(durCol)
                        val size = c.getLong(sizeCol)
                        val mime = c.getString(mimeCol) ?: ""
                        val id = c.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        
                        val ext = File(path).extension.lowercase(Locale.US)
                        if (supportedExtensions.contains(ext) || mime.contains("mp4") || mime.contains("quicktime") || mime.contains("webm")) {
                            videos.add(
                                LocalVideo(
                                    uri = contentUri,
                                    name = name,
                                    path = path,
                                    durationMs = duration,
                                    size = size,
                                    mimeType = mime
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "MediaStore query error", e)
            }
            
            // Remove duplicates and post
            val uniqueVideos = videos.distinctBy { it.path }
            _localVideos.value = uniqueVideos
        }
    }

    private fun checkRootAndApiKey() {
        viewModelScope.launch {
            _isRootAvailable.value = ShellExecutor.isRootAvailable()
            val apiKey = BuildConfig.GEMINI_API_KEY
            _isGeminiConfigured.value = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"
        }
    }

    fun setScreen(screen: String) {
        _currentScreen.value = screen
        if (screen == "TASKS") {
            _editingTask.value = null
        }
    }

    fun startTaskEdit(task: AutomationTask) {
        _editingTask.value = task
        _currentScreen.value = "ADD_TASK"
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                val appsList = resolveInfos.mapNotNull { resolveInfo ->
                    try {
                        val packageName = resolveInfo.activityInfo.packageName
                        val appLabel = resolveInfo.loadLabel(pm).toString()
                        val drawableIcon = resolveInfo.loadIcon(pm)
                        val bitmapIcon = drawableIcon.toBitmapOrNull()
                        InstalledApp(packageName, appLabel, bitmapIcon)
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.appLabel.lowercase() }
                _installedApps.value = appsList
            } catch (e: Exception) {
                Log.e(TAG, "Error loading installed apps", e)
            }
        }
    }

    // Task operations
    fun saveTask(
        name: String,
        packageName: String,
        appName: String,
        hour: Int,
        minute: Int,
        days: String,
        actionType: String,
        tapX: Float,
        tapY: Float,
        delayBeforeTapSec: Int = 3,
        mimicStepsJson: String? = null,
        validationType: String = "NONE",
        validationText: String? = null,
        validationWaitTimeSec: Int = 6,
        loopUntilSuccess: Boolean = false,
        maxAttempts: Int = 3,
        referenceImagePath: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentEditing = _editingTask.value
            val taskToSave = if (currentEditing != null) {
                currentEditing.copy(
                    name = name,
                    packageName = packageName,
                    appName = appName,
                    timeHour = hour,
                    timeMinute = minute,
                    daysOfWeek = days,
                    actionType = actionType,
                    tapX = tapX,
                    tapY = tapY,
                    delayBeforeTapSec = delayBeforeTapSec,
                    mimicSteps = mimicStepsJson ?: currentEditing.mimicSteps,
                    validationType = validationType,
                    validationText = validationText,
                    validationWaitTimeSec = validationWaitTimeSec,
                    loopUntilSuccess = loopUntilSuccess,
                    maxAttempts = maxAttempts,
                    referenceImagePath = referenceImagePath
                )
            } else {
                AutomationTask(
                    name = name,
                    packageName = packageName,
                    appName = appName,
                    timeHour = hour,
                    timeMinute = minute,
                    daysOfWeek = days,
                    actionType = actionType,
                    tapX = tapX,
                    tapY = tapY,
                    delayBeforeTapSec = delayBeforeTapSec,
                    mimicSteps = mimicStepsJson,
                    validationType = validationType,
                    validationText = validationText,
                    validationWaitTimeSec = validationWaitTimeSec,
                    loopUntilSuccess = loopUntilSuccess,
                    maxAttempts = maxAttempts,
                    referenceImagePath = referenceImagePath
                )
            }

            val id = repository.insertTask(taskToSave)
            val savedTask = if (currentEditing != null) taskToSave else taskToSave.copy(id = id.toInt())

            // Reschedule alarm
            AlarmScheduler.scheduleTask(context, savedTask)

            withContext(Dispatchers.Main) {
                _editingTask.value = null
                _currentScreen.value = "TASKS"
            }
        }
    }

    fun toggleTaskEnabled(task: AutomationTask) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(isEnabled = !task.isEnabled)
            repository.updateTask(updated)
            if (updated.isEnabled) {
                AlarmScheduler.scheduleTask(context, updated)
            } else {
                AlarmScheduler.cancelTask(context, updated)
            }
        }
    }

    fun deleteTask(task: AutomationTask) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.cancelTask(context, task)
            repository.deleteTask(task)
            
            // Delete target screenshot file if exists
            val appDir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(appDir, "target_last_frame_${task.id}.jpg")
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
        }
    }

    fun triggerTaskImmediately(task: AutomationTask) {
        val serviceIntent = Intent(context, AutomationService::class.java).apply {
            putExtra("TASK_ID", task.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun startInteractiveCoordinateCapture(taskId: Int, packageName: String, appName: String) {
        val overlayIntent = Intent(context, OverlayService::class.java).apply {
            putExtra("TASK_ID", taskId)
            putExtra("PACKAGE_NAME", packageName)
            putExtra("APP_NAME", appName)
        }
        context.startService(overlayIntent)
    }

    // Video Mimicry analysis
    fun analyzeVideoForTask(videoUri: Uri, taskId: Int, screenWidth: Int, screenHeight: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _videoAnalysisProgress.value = "جاري استيراد ملف الفيديو والتحضير للتحليل..."
            
            val result = withContext(Dispatchers.IO) {
                processVideoFile(videoUri, taskId, screenWidth, screenHeight)
            }

            _videoAnalysisProgress.value = null

            if (result != null) {
                // Save JSON to task
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    val task = db.taskDao().getTaskById(taskId)
                    if (task != null) {
                        val updated = task.copy(
                            actionType = "MIMIC_VIDEO",
                            mimicSteps = result
                        )
                        db.taskDao().updateTask(updated)
                    }
                }
                onSuccess()
            } else {
                _videoAnalysisProgress.value = "فشل تحليل الفيديو. يرجى التأكد من صلاحية الملف ومفتاح الذكاء الاصطناعي."
                withContext(Dispatchers.IO) {
                    Thread.sleep(3000)
                }
                _videoAnalysisProgress.value = null
            }
        }
    }

    private suspend fun processVideoFile(videoUri: Uri, taskId: Int, screenWidth: Int, screenHeight: Int): String? {
        val retriever = MediaMetadataRetriever()
        var tempFile: File? = null
        try {
            // Write URI to temp file because MediaMetadataRetriever works best with files or descriptors
            val inputStream = context.contentResolver.openInputStream(videoUri) ?: return null
            tempFile = File(context.cacheDir, "temp_video_$taskId.mp4")
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }

            retriever.setDataSource(tempFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 5000L

            _videoAnalysisProgress.value = "جاري استخراج اللقطات الرئيسية من الفيديو..."

            // Extract 10 keyframes distributed across duration
            val frameCount = 10
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until frameCount) {
                val timeUs = (durationMs * 1000L * i) / (frameCount - 1)
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val resized = Bitmap.createScaledBitmap(frame, 450, 800, true)
                    bitmaps.add(resized)
                }
            }

            _videoAnalysisProgress.value = "جاري استخراج لقطة النهاية الناجحة..."
            // Extract final frame at full quality for visual verification
            val lastFrame = retriever.getFrameAtTime(durationMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            if (lastFrame != null) {
                val appDir = context.getExternalFilesDir(null) ?: context.filesDir
                val targetFile = File(appDir, "target_last_frame_$taskId.jpg")
                FileOutputStream(targetFile).use { out ->
                    lastFrame.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                Log.d(TAG, "Target last frame saved to ${targetFile.absolutePath}")
            }

            _videoAnalysisProgress.value = "جاري إرسال اللقطات لـ Gemini 3.5 للتحليل..."
            return GeminiApiClient.analyzeVideoFrames(bitmaps, screenWidth, screenHeight)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing video file", e)
            return null
        } finally {
            try { retriever.release() } catch (e: Exception) {}
            try { tempFile?.delete() } catch (e: Exception) {}
        }
    }

    // Helper extension to convert Drawable to Bitmap
    private fun Drawable.toBitmapOrNull(): Bitmap? {
        if (this is BitmapDrawable) {
            return this.bitmap
        }
        return try {
            val width = if (intrinsicWidth > 0) intrinsicWidth else 128
            val height = if (intrinsicHeight > 0) intrinsicHeight else 128
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
