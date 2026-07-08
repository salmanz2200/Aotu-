package com.example.ui

import android.util.Log
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.AutomationTask
import com.example.data.ExecutionLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isRootAvailable by viewModel.isRootAvailable.collectAsState()
    val isGeminiConfigured by viewModel.isGeminiConfigured.collectAsState()
    val videoAnalysisProgress by viewModel.videoAnalysisProgress.collectAsState()

    // Elegant Dark Theme Palette
    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)) // Slate slate dark
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentScreen == "TASKS",
                    onClick = { viewModel.setScreen("TASKS") },
                    icon = { Icon(Icons.Default.List, contentDescription = "المهام") },
                    label = { Text("المهام", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF334155)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "LOGS",
                    onClick = { viewModel.setScreen("LOGS") },
                    icon = { Icon(Icons.Default.History, contentDescription = "السجل") },
                    label = { Text("السجل", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF334155)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "SETTINGS",
                    onClick = { viewModel.setScreen("SETTINGS") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") },
                    label = { Text("الإعدادات", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF334155)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    "TASKS" -> TasksListScreen(
                        viewModel = viewModel,
                        onAddTask = { viewModel.setScreen("ADD_TASK") }
                    )
                    "ADD_TASK" -> AddEditTaskScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.setScreen("TASKS") }
                    )
                    "LOGS" -> LogsScreen(
                        viewModel = viewModel
                    )
                    "SETTINGS" -> SettingsScreen(
                        viewModel = viewModel,
                        isRootAvailable = isRootAvailable,
                        isGeminiConfigured = isGeminiConfigured
                    )
                }
            }

            // Video analysis overlay
            videoAnalysisProgress?.let { progressText ->
                Dialog(onDismissRequest = {}) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = progressText,
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TasksListScreen(
    viewModel: MainViewModel,
    onAddTask: () -> Unit
) {
    val tasks by viewModel.allTasks.collectAsState()
    val logs by viewModel.allLogs.collectAsState()
    val totalRuns = logs.size
    val activeTasksCount = tasks.count { it.isEnabled }
    val totalTasksCount = tasks.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "أوتوماتور ⚡",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "تشغيل وتأتمتة التطبيقات بنقرة واحدة",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
            
            Button(
                onClick = onAddTask,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "مهمة جديدة")
                Spacer(modifier = Modifier.width(4.dp))
                Text("مهمة جديدة", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Premium Stats Dashboard Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tasks Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0F172A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "المهام",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("المهام المجدولة", fontSize = 11.sp, color = Color.Gray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$totalTasksCount",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "($activeTasksCount نشط)",
                                fontSize = 10.sp,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }
                }
            }

            // Runs Card (totalRuns using logs.size)
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0F172A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "إجمالي التشغيلات",
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("إجمالي التشغيلات", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = "$totalRuns",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color(0xFF334155), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "ابدأ",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "لا توجد مهام مجدولة بعد",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "أضف مهمتك الأولى لتشغيل أي تطبيق تلقائياً والضغط على زر محدد بدقة أو تقليد حركاتك عبر الفيديو!",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onAddTask,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("إنشاء مهمة مجدولة الآن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { viewModel.toggleTaskEnabled(task) },
                        onRun = { viewModel.triggerTaskImmediately(task) },
                        onEdit = { viewModel.startTaskEdit(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskRow(
    task: AutomationTask,
    onToggle: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val daysText = remember(task.daysOfWeek) {
        val daysList = task.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (daysList.size == 7) {
            "يومياً"
        } else {
            val dayNames = listOf("إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت", "أحد")
            daysList.mapNotNull { dayNum ->
                if (dayNum in 1..7) dayNames[dayNum - 1] else null
            }.joinToString(", ")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // First row: App Details & Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Logo Placeholder / Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF334155), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = "رمز التطبيق",
                        tint = if (task.isEnabled) Color(0xFF38BDF8) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${task.appName} (${task.packageName})",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = task.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF38BDF8),
                        checkedTrackColor = Color(0xFF0284C7),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color(0xFF334155), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Second row: Schedule Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "الوقت",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val isPm = task.timeHour >= 12
                        val displayHour = when {
                            task.timeHour == 0 -> 12
                            task.timeHour > 12 -> task.timeHour - 12
                            else -> task.timeHour
                        }
                        val periodText = if (isPm) "PM" else "AM"
                        Text(
                            text = String.format(Locale.US, "%02d:%02d %s", displayHour, task.timeMinute, periodText),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Text(
                        text = daysText,
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (task.actionType == "TAP") "ضغط إحداثي (${task.tapX.toInt()}, ${task.tapY.toInt()})" else "محاكاة بالفيديو 🎥",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    
                    if (task.lastRunTime > 0) {
                        val sdf = SimpleDateFormat("hh:mm a - yyyy/MM/dd", Locale.US)
                        val dateStr = sdf.format(Date(task.lastRunTime))
                        Text(
                            text = "آخر تنفيذ: $dateStr (${if (task.lastRunStatus == "SUCCESS") "نجح" else "فشل"})",
                            fontSize = 10.sp,
                            color = if (task.lastRunStatus == "SUCCESS") Color(0xFF4ADE80) else Color(0xFFF87171)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Third row: Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRun,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "تشغيل الآن",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تشغيل الآن", fontSize = 12.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF334155), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color.White)
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF7F1D1D), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFF87171))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف المهمة") },
            text = { Text("هل أنت متأكد من رغبتك في حذف المهمة '${task.name}'؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val editingTask by viewModel.editingTask.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var name by remember { mutableStateOf(editingTask?.name ?: "") }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var hour by remember { mutableStateOf(editingTask?.timeHour ?: 12) }
    var minute by remember { mutableStateOf(editingTask?.timeMinute ?: 0) }
    var selectedDays by remember {
        mutableStateOf(
            editingTask?.daysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
                ?: setOf(1, 2, 3, 4, 5) // Mon-Fri default
        )
    }
    var actionType by remember { mutableStateOf(editingTask?.actionType ?: "TAP") } // "TAP" or "MIMIC_VIDEO"
    var tapX by remember { mutableStateOf(editingTask?.tapX?.toString() ?: "500") }
    var tapY by remember { mutableStateOf(editingTask?.tapY?.toString() ?: "1200") }
    var validationType by remember { mutableStateOf(editingTask?.validationType ?: "NONE") }
    var validationText by remember { mutableStateOf(editingTask?.validationText ?: "") }
    var validationWaitTimeSec by remember { mutableStateOf(editingTask?.validationWaitTimeSec ?: 6) }
    var loopUntilSuccess by remember { mutableStateOf(editingTask?.loopUntilSuccess ?: false) }
    var maxAttempts by remember { mutableStateOf(editingTask?.maxAttempts ?: 3) }
    var referenceImagePath by remember { mutableStateOf(editingTask?.referenceImagePath) }
    var delayBeforeTapSec by remember { mutableStateOf(editingTask?.delayBeforeTapSec ?: 3) }
    var isRecurring by remember { mutableStateOf(editingTask?.isRecurring ?: true) }

    var showAppPicker by remember { mutableStateOf(false) }
    var showVideoPicker by remember { mutableStateOf(false) }

    // Screen dimension logic for video analysis
    val config = LocalConfiguration.current
    val density = context.resources.displayMetrics.density
    val screenWidthPx = (config.screenWidthDp * density).toInt()
    val screenHeightPx = (config.screenHeightDp * density).toInt()

    // Initialize editing details
    LaunchedEffect(editingTask, installedApps) {
        editingTask?.let { task ->
            selectedApp = installedApps.find { it.packageName == task.packageName }
            validationType = task.validationType
            validationText = task.validationText ?: ""
            validationWaitTimeSec = task.validationWaitTimeSec
            loopUntilSuccess = task.loopUntilSuccess
            maxAttempts = task.maxAttempts
            referenceImagePath = task.referenceImagePath
            delayBeforeTapSec = task.delayBeforeTapSec
            isRecurring = task.isRecurring
        }
    }

    // Video selector launcher
    val videoSelectorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val currentTask = editingTask
            if (currentTask != null) {
                viewModel.analyzeVideoForTask(
                    videoUri = uri,
                    taskId = currentTask.id,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx,
                    onSuccess = {
                        Toast.makeText(context, "تم تحليل الفيديو وحفظ الخطوات التلقائية بنجاح!", Toast.LENGTH_LONG).show()
                    }
                )
            } else {
                Toast.makeText(context, "يرجى حفظ المهمة أولاً كمهمة تقليد فيديو قبل رفع الفيديو لتحليله.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Image selector launcher for reference success image
    val imageSelectorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Wrap openInputStream in .use {} block to guarantee safe and deterministic closing of resources
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val appDir = context.getExternalFilesDir(null) ?: context.filesDir
                    val taskIdSuffix = editingTask?.id ?: System.currentTimeMillis()
                    val destFile = File(appDir, "custom_ref_$taskIdSuffix.jpg")
                    destFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    referenceImagePath = destFile.absolutePath
                    Toast.makeText(context, "تم حفظ الصورة المرجعية بنجاح! 🖼️", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainScreen", "Error copying reference image", e)
                Toast.makeText(context, "فشل حفظ الصورة المرجعية: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color(0xFF334155), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "تراجع", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = if (editingTask != null) "تعديل مهمة مجدولة" else "إضافة مهمة جديدة",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Task Name Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("اسم المهمة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("مثال: تشغيل اللعبة والضغط تلقائياً") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            )
                        )
                    }
                }
            }

            // Target App Picker Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("التطبيق المستهدف", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(10.dp))

                        if (selectedApp != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                    .clickable { showAppPicker = true }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF334155), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Android, contentDescription = "أيقونة", tint = Color(0xFF38BDF8))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(selectedApp!!.appLabel, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(selectedApp!!.packageName, fontSize = 11.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = "تم الاختيار", tint = Color(0xFF4ADE80))
                            }
                        } else {
                            Button(
                                onClick = { showAppPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "اختر التطبيق")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اختر التطبيق من القائمة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Time & Days Schedule
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("وقت وأيام التكرار", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom elegant time sliders/adjusters in 12-hour format with AM/PM
                        val isPm = hour >= 12
                        val displayHour = when {
                            hour == 0 -> 12
                            hour > 12 -> hour - 12
                            else -> hour
                        }
                        
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Hours Wheel Picker
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val hourItems = remember { (1..12).map { String.format(Locale.US, "%02d", it) } }
                                    ScrollableWheelPicker(
                                        items = hourItems,
                                        currentIndex = (displayHour - 1).coerceIn(0, 11),
                                        onItemSelected = { index ->
                                            val selectedDisplayHour = index + 1
                                            hour = if (isPm) {
                                                if (selectedDisplayHour == 12) 12 else selectedDisplayHour + 12
                                            } else {
                                                if (selectedDisplayHour == 12) 0 else selectedDisplayHour
                                            }
                                        },
                                        modifier = Modifier.width(70.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("الساعة", fontSize = 11.sp, color = Color.Gray)
                                }

                                Spacer(modifier = Modifier.width(12.dp))
                                Text(":", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
                                Spacer(modifier = Modifier.width(12.dp))

                                // Minutes Wheel Picker
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val minuteItems = remember { (0..59).map { String.format(Locale.US, "%02d", it) } }
                                    ScrollableWheelPicker(
                                        items = minuteItems,
                                        currentIndex = minute.coerceIn(0, 59),
                                        onItemSelected = { index ->
                                            minute = index
                                        },
                                        modifier = Modifier.width(70.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("الدقيقة", fontSize = 11.sp, color = Color.Gray)
                                }

                                Spacer(modifier = Modifier.width(20.dp))

                                // AM/PM Column
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            hour = if (isPm) hour - 12 else hour + 12
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF334155),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isPm) "PM" else "AM",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("الفترة", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("تكرار في أيام:", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Day of week chips (Mon=1, Sun=7)
                        val daysMap = listOf(
                            1 to "إثن", 2 to "ثلا", 3 to "أرب",
                            4 to "خمس", 5 to "جمع", 6 to "سبت", 7 to "أحد"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            daysMap.forEach { (dayId, dayName) ->
                                val isSelected = selectedDays.contains(dayId)
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                                            CircleShape
                                        )
                                        .clickable {
                                            selectedDays = if (isSelected) {
                                                selectedDays - dayId
                                            } else {
                                                selectedDays + dayId
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Automation Action Customizer (Tap vs Mimic Video)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("طريقة عمل التشغيل التلقائي", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Segmented Control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (actionType == "TAP") Color(0xFF1E293B) else Color.Transparent)
                                    .clickable { actionType = "TAP" }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "نقر على إحداثيات محددة",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (actionType == "TAP") Color(0xFF38BDF8) else Color.Gray
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (actionType == "MIMIC_VIDEO") Color(0xFF1E293B) else Color.Transparent)
                                    .clickable { actionType = "MIMIC_VIDEO" }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "تقليد الحركات بالفيديو الذكي",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (actionType == "MIMIC_VIDEO") Color(0xFF38BDF8) else Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (actionType == "TAP") {
                            // Coordinate selectors
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = tapX,
                                    onValueChange = { tapX = it },
                                    label = { Text("الموقع الأفقي X (بكسل)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF334155)
                                    )
                                )
                                OutlinedTextField(
                                    value = tapY,
                                    onValueChange = { tapY = it },
                                    label = { Text("الموقع العمودي Y (بكسل)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF334155)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = if (delayBeforeTapSec == 0) "" else delayBeforeTapSec.toString(),
                                onValueChange = { delayBeforeTapSec = it.toIntOrNull() ?: 0 },
                                label = { Text("انتظار قبل الضغط (ثواني) ⏱️") },
                                placeholder = { Text("مثال: 3") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF334155)
                                )
                            )
                            Text(
                                "المدة الزمنية التي ينتظرها النظام بعد تشغيل التطبيق وقبل إرسال نقرة الضغط على X وY للتأكد من اكتمال تحميل الشاشة.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // INTERACTIVE OVERLAY LAUNCHER BUTTON!
                            if (selectedApp != null) {
                                Button(
                                    onClick = {
                                        if (editingTask != null) {
                                            viewModel.startInteractiveCoordinateCapture(
                                                taskId = editingTask!!.id,
                                                packageName = selectedApp!!.packageName,
                                                appName = selectedApp!!.appLabel
                                            )
                                        } else {
                                            Toast.makeText(context, "يرجى أولاً حفظ هذه المهمة مؤقتاً لتتمكن من اختيار الإحداثيات تفاعلياً.", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "تحديد تفاعلي")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("تحديد تفاعلي (افتح التطبيق واختر بالضغط)", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    "يرجى تحديد تطبيق أولاً لاستخدام التحديد التفاعلي للموقع.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            // Video mimicry configuration
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (editingTask != null) {
                                                showVideoPicker = true
                                            } else {
                                                Toast.makeText(context, "يرجى حفظ المهمة أولاً كمهمة تقليد فيديو قبل رفع الفيديو.", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.VideoLibrary, contentDescription = "فيديو", tint = Color(0xFF38BDF8), modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (editingTask?.mimicSteps != null) "تم تحليل وتحميل حركات الفيديو بنجاح! اضغط للتغيير" else "اضغط لرفع مقطع فيديو مسجل للمهمة",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (editingTask?.mimicSteps != null) Color(0xFF4ADE80) else Color.LightGray
                                        )
                                        Text("سيقوم الذكاء الاصطناعي باستخراج النقرات والسرعات تلقائياً", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                                
                                if (editingTask?.mimicSteps == null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "ملاحظة: لرفع الفيديو، يجب أولاً حفظ المهمة مجدولة فارغة ثم الضغط على 'تعديل المهمة' لرفع الفيديو.",
                                        fontSize = 11.sp,
                                        color = Color.Yellow.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // آلية التحقق من النجاح (Success Verification Card)
            // -------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "تحقق", tint = Color(0xFF4ADE80))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "آلية التحقق من نجاح المهمة 🛡️",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "يساعد هذا الخيار على التأكد من إتمام العملية بالكامل قبل إنهاء التشغيل التلقائي. في حال الفشل، يمكن تكرار المحاولة حتى ينجح.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Segmented control for choosing validation type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            val types = listOf(
                                "NONE" to "بدون تحقق 🚫",
                                "TEXT" to "تحقق بالنص 📝",
                                "IMAGE" to "صورة مرجعية 🖼️"
                            )
                            types.forEach { (typeVal, label) ->
                                val isSelected = validationType == typeVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
                                        .clickable { validationType = typeVal }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color(0xFF38BDF8) else Color.Gray
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (validationType == "TEXT") {
                            OutlinedTextField(
                                value = validationText,
                                onValueChange = { validationText = it },
                                label = { Text("كلمة أو نص نجاح التأكيد المطلوب") },
                                placeholder = { Text("مثال: تم ، نجح ، تمت العملية ، موافق...") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF334155)
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "سيقوم الذكاء الاصطناعي بالتقاط صورة للشاشة والبحث عن هذا النص لتأكيد النجاح.",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        } else if (validationType == "IMAGE") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, Color(0xFF334155)), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = "صورة", tint = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "صورة شاشة النجاح المرجعية",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            if (!referenceImagePath.isNullOrBlank()) "تم تحديد صورة مخصصة للنجاح ✅" 
                                            else "يرجى رفع صورة للنجاح، أو سيتم مقارنتها باللقطة الأخيرة المستخرجة من الفيديو تلقائياً.",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }

                                if (!referenceImagePath.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            "صورة مخصصة مرفوعة:",
                                            fontSize = 9.sp,
                                            color = Color.LightGray,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            File(referenceImagePath!!).name,
                                            fontSize = 9.sp,
                                            color = Color(0xFF4ADE80),
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { referenceImagePath = null },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "حذف",
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { imageSelectorLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text("ارفع صورة النجاح المخصصة 📤", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        if (validationType != "NONE") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color(0xFF334155), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(16.dp))

                            // ----------------- CUSTOMIZABLE WAIT TIME -----------------
                            Text("وقت الانتظار بعد تنفيذ الضغط ⏳", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "المدة التي سينتظرها التطبيق لتتحمل شاشة النجاح بالكامل قبل البدء في التحقق.",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                var isMinSelected by remember { mutableStateOf(validationWaitTimeSec >= 60) }
                                val displayVal = if (isMinSelected) (validationWaitTimeSec / 60).coerceIn(1, 60) else validationWaitTimeSec.coerceIn(1, 59)
                                
                                val maxVal = if (isMinSelected) 60 else 59
                                val waitTimeItems = remember(isMinSelected) { (1..maxVal).map { "$it" } }

                                key(isMinSelected) {
                                    ScrollableWheelPicker(
                                        items = waitTimeItems,
                                        currentIndex = (displayVal - 1).coerceIn(0, maxVal - 1),
                                        onItemSelected = { index ->
                                            val newDisplayVal = index + 1
                                            validationWaitTimeSec = if (isMinSelected) newDisplayVal * 60 else newDisplayVal
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Unit segmented control (Seconds vs Minutes)
                                Column(
                                    modifier = Modifier
                                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (!isMinSelected) Color(0xFF1E293B) else Color.Transparent)
                                            .clickable {
                                                isMinSelected = false
                                                validationWaitTimeSec = 6 // reset to nice default of 6 seconds
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("ثانية", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isMinSelected) Color(0xFF38BDF8) else Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isMinSelected) Color(0xFF1E293B) else Color.Transparent)
                                            .clickable {
                                                isMinSelected = true
                                                validationWaitTimeSec = 60 // reset to nice default of 1 minute
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("دقيقة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isMinSelected) Color(0xFF38BDF8) else Color.Gray)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color(0xFF334155), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(16.dp))

                            // ----------------- RECURRING TASK MODE -----------------
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("تكرار المهمة تلقائياً (مهمة متكررة) 🔁", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        "إذا تم تفعيل هذا الخيار، ستتم إعادة جدولة المهمة تلقائياً لليوم التالي بعد كل تنفيذ بنجاح أو فشل.",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        lineHeight = 14.sp
                                    )
                                }
                                Switch(
                                    checked = isRecurring,
                                    onCheckedChange = { isRecurring = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF4ADE80),
                                        checkedTrackColor = Color(0xFF0F172A)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color(0xFF334155), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(16.dp))

                            // ----------------- LOOP / FOLLOW-UP MODE -----------------
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("وضع المتابعة المستمرة (تابع حتى النجاح) 🔄", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        "يراقب الشاشة كل ٣٠ ثانية. إذا ظهرت شاشة النجاح ينهي المهمة، وإلا يعيد الضغط ويكرر العملية.",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        lineHeight = 14.sp
                                    )
                                }
                                Switch(
                                    checked = loopUntilSuccess,
                                    onCheckedChange = { loopUntilSuccess = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF4ADE80),
                                        checkedTrackColor = Color(0xFF0F172A)
                                    )
                                )
                            }

                            if (loopUntilSuccess) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Text("الحد الأقصى لإعادة المحاولة:", fontSize = 11.sp, color = Color.LightGray, modifier = Modifier.weight(1f))
                                    
                                    val attemptsOptions = listOf(2, 3, 5, 10)
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                    ) {
                                        attemptsOptions.forEach { opt ->
                                            val isAttemptSelected = maxAttempts == opt
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isAttemptSelected) Color(0xFF0F172A) else Color.Transparent)
                                                    .clickable { maxAttempts = opt }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "$opt",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isAttemptSelected) Color(0xFF4ADE80) else Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Action Button
        Button(
            onClick = {
                if (name.isEmpty() || selectedApp == null) {
                    Toast.makeText(context, "يرجى كتابة اسم واختيار تطبيق مستهدف للمهمة", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                // Mandatory validation: Ensure that the task is scheduled on at least one day before saving to DB
                if (selectedDays.isEmpty()) {
                    Toast.makeText(context, "يرجى اختيار يوم واحد على الأقل لتشغيل المهمة", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                viewModel.saveTask(
                    name = name,
                    packageName = selectedApp!!.packageName,
                    appName = selectedApp!!.appLabel,
                    hour = hour,
                    minute = minute,
                    days = selectedDays.sorted().joinToString(","),
                    actionType = actionType,
                    tapX = tapX.toFloatOrNull() ?: 500f,
                    tapY = tapY.toFloatOrNull() ?: 1200f,
                    delayBeforeTapSec = delayBeforeTapSec,
                    validationType = validationType,
                    validationText = if (validationType == "TEXT") validationText else null,
                    validationWaitTimeSec = validationWaitTimeSec,
                    loopUntilSuccess = loopUntilSuccess,
                    maxAttempts = maxAttempts,
                    referenceImagePath = if (validationType == "IMAGE") referenceImagePath else null,
                    isRecurring = isRecurring
                )
                Toast.makeText(context, "تم حفظ وجدولة المهمة بنجاح!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("حفظ وجدولة المهمة تلقائياً 💾", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }
    }

    // App Picker Dialog
    if (showAppPicker) {
        Dialog(onDismissRequest = { showAppPicker = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "اختر التطبيق المطلوب",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    var searchQuery by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ابحث عن تطبيق...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val filteredApps = installedApps.filter {
                        it.appLabel.lowercase().contains(searchQuery.lowercase()) ||
                                it.packageName.lowercase().contains(searchQuery.lowercase())
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F172A))
                                    .clickable {
                                        selectedApp = app
                                        showAppPicker = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF334155), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Android, contentDescription = app.appLabel, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(app.appLabel, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVideoPicker) {
        VideoPickerDialog(
            viewModel = viewModel,
            onDismiss = { showVideoPicker = false },
            onVideoSelected = { uri ->
                showVideoPicker = false
                viewModel.analyzeVideoForTask(
                    videoUri = uri,
                    taskId = editingTask!!.id,
                    screenWidth = screenWidthPx,
                    screenHeight = screenHeightPx,
                    onSuccess = {
                        Toast.makeText(context, "تم تحليل الفيديو وحفظ الخطوات التلقائية بنجاح!", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}

@Composable
fun VideoPickerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onVideoSelected: (Uri) -> Unit
) {
    val localVideos by viewModel.localVideos.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Videos, 1 = Collections
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    
    // Scan when opened
    LaunchedEffect(Unit) {
        viewModel.scanLocalVideos()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختر مقطع فيديو 🎥",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    IconButton(
                        onClick = { viewModel.scanLocalVideos() },
                        modifier = Modifier.background(Color(0xFF334155), CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tabs: Videos ("الفيديوهات") and Collections ("المجموعات")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) Color(0xFF0284C7) else Color.Transparent)
                            .clickable {
                                selectedTab = 0
                                selectedFolder = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "الفيديوهات",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "المجموعات",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                if (localVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.VideoFile,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "لم يتم العثور على فيديوهات",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "تأكد من وجود فيديوهات بصيغة mp4 أو mov أو webm في مجلد التنزيلات أو الأفلام بالهاتف.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        if (selectedTab == 0) {
                            // All Videos
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(localVideos) { video ->
                                    VideoGridItem(video = video) {
                                        onVideoSelected(video.uri)
                                    }
                                }
                            }
                        } else {
                            // Collections/Groups
                            if (selectedFolder != null) {
                                // Videos within selected folder
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedFolder = null }
                                            .padding(bottom = 12.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "تراجع", tint = Color(0xFF38BDF8))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            selectedFolder!!,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                    
                                    val folderVideos = localVideos.filter { getParentFolderName(it.path) == selectedFolder }
                                    
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(folderVideos) { video ->
                                            VideoGridItem(video = video) {
                                                onVideoSelected(video.uri)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Folder Groups
                                val groups = localVideos.groupBy { getParentFolderName(it.path) }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(groups.keys.toList()) { folderName ->
                                        val count = groups[folderName]?.size ?: 0
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedFolder = folderName },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    folderName,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    "$count فيديو",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Dismiss action
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun VideoGridItem(video: LocalVideo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                VideoThumbnail(videoUri = video.uri, modifier = Modifier.fillMaxSize())
                
                // Small duration tag overlaid on the bottom right of the thumbnail
                val durationText = formatDuration(video.durationMs)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Name of the video
            Text(
                text = video.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            
            // Duration under the thumbnail as requested
            Text(
                text = "المدة: " + formatDuration(video.durationMs),
                fontSize = 9.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
            )
        }
    }
}

object ThumbnailCache {
    private val cache = android.util.LruCache<String, Bitmap>(50) // Cache up to 50 items

    fun get(uriString: String): Bitmap? = cache.get(uriString)
    fun put(uriString: String, bitmap: Bitmap) {
        cache.put(uriString, bitmap)
    }
}

@Composable
fun VideoThumbnail(videoUri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var thumbnail by remember(videoUri) { mutableStateOf<Bitmap?>(ThumbnailCache.get(videoUri.toString())) }
    
    if (thumbnail == null) {
        LaunchedEffect(videoUri) {
            withContext(Dispatchers.IO) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, videoUri)
                    val frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val scaled = Bitmap.createScaledBitmap(frame, 120, 120, true)
                        ThumbnailCache.put(videoUri.toString(), scaled)
                        thumbnail = scaled
                    }
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    try { retriever?.release() } catch (ex: Exception) {}
                }
            }
        }
    }
    
    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color(0xFF38BDF8).copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun getParentFolderName(path: String): String {
    return try {
        val file = File(path)
        file.parentFile?.name ?: "غير معروف"
    } catch (e: Exception) {
        "غير معروف"
    }
}

@Composable
fun LogsScreen(viewModel: MainViewModel) {
    val logs by viewModel.allLogs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "سجل العمليات 📜",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "سرد تاريخي لجميع المهام التي تم تشغيلها تلقائياً",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }

            if (logs.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearLogs() },
                    modifier = Modifier.background(Color(0xFF7F1D1D), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "مسح الكل", tint = Color(0xFFF87171))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "السجل فارغ",
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "السجل فارغ تماماً",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Text(
                        text = "ستظهر هنا تفاصيل تنفيذ مهامك المجدولة ونتائجها.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    LogCard(log)
                }
            }
        }
    }
}

@Composable
fun LogCard(log: ExecutionLog) {
    val sdf = SimpleDateFormat("hh:mm:ss a - yyyy/MM/dd", Locale.US)
    val timestampStr = sdf.format(Date(log.timestamp))

    val isSuccess = log.status == "SUCCESS"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (isSuccess) Color(0xFF14532D) else Color(0xFF7F1D1D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = log.status,
                        tint = if (isSuccess) Color(0xFF4ADE80) else Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.taskName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${log.packageName} • $timestampStr",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSuccess) Color(0xFF064E3B) else Color(0xFF450A0A))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isSuccess) "ناجح" else "فشل",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuccess) Color(0xFF34D399) else Color(0xFFF87171)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = log.message,
                fontSize = 12.sp,
                color = Color.LightGray,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    isRootAvailable: Boolean,
    isGeminiConfigured: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "لوحة التحكم والإعدادات ⚙️",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Root permission Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "صلاحيات الروت",
                        tint = if (isRootAvailable) Color(0xFF4ADE80) else Color(0xFFF87171),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("صلاحية الـ Root من Magisk / KernelSU", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Text(
                            if (isRootAvailable) "نشطة وممنوحة (تعمل بكفاءة)" else "غير ممنوحة (يتم المحاكاة للتطوير)",
                            fontSize = 11.sp,
                            color = if (isRootAvailable) Color(0xFF4ADE80) else Color(0xFFF87171)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "يتطلب أوتوماتور صلاحيات Root كاملة لمحاكاة النقرات الفعلية بدقة على الشاشة وقفلها وتشغيل الأزرار وتجاوز ضغط المستخدم. في حال عدم وجودها، سيعمل التطبيق في وضع المحاكاة اللطيف للتجربة.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp
                )
            }
        }

        // Gemini API Configuration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "الذكاء الاصطناعي",
                        tint = if (isGeminiConfigured) Color(0xFF38BDF8) else Color.Yellow,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("الذكاء الاصطناعي (Gemini 3.5)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Text(
                            if (isGeminiConfigured) "تم التكوين (مفتاح الـ API نشط)" else "بانتظار تكوين مفتاح الـ API",
                            fontSize = 11.sp,
                            color = if (isGeminiConfigured) Color(0xFF4ADE80) else Color.Yellow
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "يُستخدم الذكاء الاصطناعي لتحليل مقاطع الفيديو التي تسجلها لنفسك أثناء تأدية مهامك على الهاتف واستخراج النقرات التلقائية والتحقق من النتيجة النهائية للشاشة ومطابقتها. يرجى تكوين مفتاح GEMINI_API_KEY في لوحة أسرار AI Studio (Secrets Panel) لتفعيل الميزة مستقلة بالكامل بدون كمبيوتر.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp
                )
            }
        }

        // About developers Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "حول أوتوماتور 🚀",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "• تطبيق محلي مستقل بالكامل بدون الحاجة لتوصيل الهاتف بالكمبيوتر أو تشغيل Metro Bundler.\n" +
                            "• دمج كود الأتمتة والذكاء الاصطناعي مباشرة في الـ APK.\n" +
                            "• يعمل بكفاءة بالخلفية وأثناء نوم الهاتف والإنعاش التلقائي بعد إعادة التشغيل.",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollableWheelPicker(
    items: List<String>,
    currentIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 3,
    itemHeight: Dp = 45.dp
) {
    if (items.isEmpty()) return

    val virtualCount = items.size * 10000
    val startOffset = remember(items, currentIndex) {
        val half = virtualCount / 2
        half - (half % items.size) + currentIndex
    }

    val state = rememberLazyListState(initialFirstVisibleItemIndex = startOffset)
    
    // Programmatic sync when currentIndex changes from outside
    LaunchedEffect(currentIndex) {
        val currentVirtualIndex = state.firstVisibleItemIndex
        val currentMod = currentVirtualIndex % items.size
        if (currentMod != currentIndex) {
            val diff = currentIndex - currentMod
            val targetIndex = currentVirtualIndex + diff
            state.scrollToItem(targetIndex)
        }
    }

    // Callback when scroll finishes
    val isScrollInProgress = state.isScrollInProgress
    LaunchedEffect(isScrollInProgress) {
        if (!isScrollInProgress) {
            val centeredVirtualIndex = state.firstVisibleItemIndex
            val centeredIndex = centeredVirtualIndex % items.size
            if (centeredIndex != currentIndex) {
                onItemSelected(centeredIndex)
            }
        }
    }

    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemsCount)
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Selection overlay background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Color(0xFF1E293B))
        )

        LazyColumn(
            state = state,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2)),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(virtualCount) { virtualIndex ->
                val index = virtualIndex % items.size
                val isSelected = state.firstVisibleItemIndex == virtualIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        fontSize = if (isSelected) 18.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF38BDF8) else Color.Gray
                    )
                }
            }
        }
    }
}
