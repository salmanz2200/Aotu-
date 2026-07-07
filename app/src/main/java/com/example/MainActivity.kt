package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request required permissions (Notifications & Media/Files Access) on startup
        val requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "يرجى تفعيل صلاحيات الوصول للملفات والإشعارات لضمان عمل كافة ميزات الأوتوماتور",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val permissionsList = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(android.Manifest.permission.POST_NOTIFICATIONS)
            permissionsList.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissionsList.add(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionsList.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        requestPermissionsLauncher.launch(permissionsList.toTypedArray())

        // Proactively ask for Draw Over Other Apps permission if we can
        requestOverlayPermission()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            Toast.makeText(
                this,
                "يرجى تمكين ميزة الظهور فوق التطبيقات لتتمكن من تحديد الإحداثيات بالضغط تفاعلياً",
                Toast.LENGTH_LONG
            ).show()
            startActivity(intent)
        }
    }
}
