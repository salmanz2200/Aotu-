package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val TAG = "OverlayService"
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val myViewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = myViewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getIntExtra("TASK_ID", -1) ?: -1
        val packageName = intent?.getStringExtra("PACKAGE_NAME") ?: ""
        val appName = intent?.getStringExtra("APP_NAME") ?: "التطبيق"

        Log.d(TAG, "OverlayService Started. taskId=$taskId, targetPackage=$packageName")

        if (taskId == -1 || packageName.isEmpty()) {
            Toast.makeText(this, "بيانات المهمة غير صالحة للأوتوماتور", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Open the target application first
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "تعذر تشغيل التطبيق المستهدف تلقائياً", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching target app", e)
        }

        // 2. Show the floating overlay
        showCaptureOverlay(taskId, packageName, appName)

        return START_NOT_STICKY
    }

    private fun showCaptureOverlay(taskId: Int, packageName: String, appName: String) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
        }

        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            
            // Set the view tree owners required by Jetpack Compose
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                MyApplicationTheme {
                    CoordinateCaptureScreen(
                        appName = appName,
                        onCancel = {
                            removeOverlayAndStop()
                            bringMainActivityToForeground()
                        },
                        onSave = { x, y ->
                            saveCoordinates(taskId, x, y)
                        }
                    )
                }
            }
        }

        // Move lifecycle to started and resumed states so ComposeView compiles and starts drawing
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        try {
            windowManager?.addView(composeView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add WindowManager overlay. Make sure Draw Over Apps permission is granted.", e)
            Toast.makeText(this, "فشل إظهار الشاشة الشفافة. يرجى تفعيل صلاحية الظهور فوق التطبيقات", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun saveCoordinates(taskId: Int, x: Float, y: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(this@OverlayService)
            val task = db.taskDao().getTaskById(taskId)
            if (task != null) {
                val updated = task.copy(tapX = x, tapY = y)
                db.taskDao().updateTask(updated)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@OverlayService,
                        "تم حفظ الموقع بنجاح: (${x.toInt()}, ${y.toInt()})",
                        Toast.LENGTH_LONG
                    ).show()
                    removeOverlayAndStop()
                    bringMainActivityToForeground()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OverlayService, "فشل حفظ الإحداثيات: لم يتم العثور على المهمة", Toast.LENGTH_SHORT).show()
                    removeOverlayAndStop()
                }
            }
        }
    }

    private fun removeOverlayAndStop() {
        try {
            composeView?.let {
                windowManager?.removeView(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing view", e)
        } finally {
            composeView = null
            stopSelf()
        }
    }

    private fun bringMainActivityToForeground() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error returning to main app", e)
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        myViewModelStore.clear()
        super.onDestroy()
        removeOverlayAndStop()
    }
}

@Composable
fun CoordinateCaptureScreen(
    appName: String,
    onCancel: () -> Unit,
    onSave: (Float, Float) -> Unit
) {
    var selectedPoint by remember { mutableStateOf<Offset?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x22000000)) // Very subtle dark overlay
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    selectedPoint = offset
                    showDialog = true
                }
            }
    ) {
        // Full screen Canvas to draw targets
        Canvas(modifier = Modifier.fillMaxSize()) {
            selectedPoint?.let { point ->
                // Draw crosshair/target at touch point
                drawCircle(
                    color = Color.Red,
                    radius = 16f,
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 24f,
                    center = point,
                    style = Stroke(width = 4f)
                )
                drawCircle(
                    color = Color.Red,
                    radius = 48f,
                    center = point,
                    style = Stroke(width = 2f)
                )
            }
        }

        // Header Instructions Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "أوتوماتور: تحديد موقع الزر لـ $appName",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "اضغط في أي مكان على الشاشة لتحديد موقع النقر التلقائي",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("إلغاء وتراجع", fontSize = 12.sp, color = Color.White)
                }
            }
        }

        // Save Confirmation dialog overlay inside the service view!
        if (showDialog && selectedPoint != null) {
            Card(
                modifier = Modifier
                    .width(300.dp)
                    .align(Alignment.Center)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "تأكيد الموقع المحدد",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "الإحداثيات الملتقطة:\nX = ${selectedPoint!!.x.toInt()}  |  Y = ${selectedPoint!!.y.toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "هل تريد اعتماد هذا الموقع لمهمة الضغط التلقائي؟",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = {
                                showDialog = false
                                selectedPoint = null
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("محاولة أخرى", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                onSave(selectedPoint!!.x, selectedPoint!!.y)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("حفظ واعتماد", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
