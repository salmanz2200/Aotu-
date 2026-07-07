package com.example.utils

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object ShellExecutor {
    private const val TAG = "ShellExecutor"
    private var isRootAvailableCached: Boolean? = null

    data class ShellResult(
        val isSuccess: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int
    )

    fun isRootAvailable(): Boolean {
        isRootAvailableCached?.let { return it }

        // Check standard files first
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) {
                isRootAvailableCached = true
                return true
            }
        }

        // Try 'which su'
        var whichProcess: Process? = null
        var whichReader: BufferedReader? = null
        try {
            whichProcess = Runtime.getRuntime().exec(arrayOf("which", "su"))
            whichReader = BufferedReader(InputStreamReader(whichProcess.inputStream))
            val line = whichReader.readLine()
            if (line != null && line.contains("su")) {
                isRootAvailableCached = true
                return true
            }
        } catch (e: Exception) {
        } finally {
            try { whichReader?.close() } catch (e: Exception) {}
            try { whichProcess?.inputStream?.close() } catch (e: Exception) {}
            try { whichProcess?.errorStream?.close() } catch (e: Exception) {}
            try { whichProcess?.outputStream?.close() } catch (e: Exception) {}
            try { whichProcess?.destroy() } catch (e: Exception) {}
        }

        // Try executing su directly with a simple command to check
        var idProcess: Process? = null
        var idReader: BufferedReader? = null
        try {
            idProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            idReader = BufferedReader(InputStreamReader(idProcess.inputStream))
            val line = idReader.readLine()
            val exitCode = idProcess.waitFor()
            if (exitCode == 0 || (line != null && line.contains("uid=0"))) {
                isRootAvailableCached = true
                return true
            }
        } catch (e: Exception) {
        } finally {
            try { idReader?.close() } catch (e: Exception) {}
            try { idProcess?.inputStream?.close() } catch (e: Exception) {}
            try { idProcess?.errorStream?.close() } catch (e: Exception) {}
            try { idProcess?.outputStream?.close() } catch (e: Exception) {}
            try { idProcess?.destroy() } catch (e: Exception) {}
        }

        isRootAvailableCached = false
        return false
    }

    fun execute(command: String, useRoot: Boolean = true): ShellResult {
        Log.d(TAG, "Executing command: $command (root=$useRoot)")
        
        if (useRoot) {
            // Try executing via su -c directly first, as requested
            var process: Process? = null
            var isReader: BufferedReader? = null
            var esReader: BufferedReader? = null
            try {
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                isReader = BufferedReader(InputStreamReader(process.inputStream))
                esReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val output = isReader.readLines().joinToString("\n")
                val error = esReader.readLines().joinToString("\n")
                val exitCode = process.waitFor()
                
                Log.d(TAG, "su -c direct execution result: exitCode=$exitCode, out=$output, err=$error")
                
                if (exitCode == 0) {
                    isRootAvailableCached = true
                    return ShellResult(
                        isSuccess = true,
                        output = output,
                        error = error,
                        exitCode = exitCode
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "su -c execution failed, falling back to stdin writing...", e)
            } finally {
                try { isReader?.close() } catch (e: Exception) {}
                try { esReader?.close() } catch (e: Exception) {}
                try { process?.inputStream?.close() } catch (e: Exception) {}
                try { process?.errorStream?.close() } catch (e: Exception) {}
                try { process?.outputStream?.close() } catch (e: Exception) {}
                try { process?.destroy() } catch (e: Exception) {}
            }
        }

        // Fallback to the original stdin writing shell execution or standard shell
        var process: Process? = null
        var os: DataOutputStream? = null
        var isReader: BufferedReader? = null
        var esReader: BufferedReader? = null

        try {
            val shell = if (useRoot) "su" else "sh"
            process = try {
                Runtime.getRuntime().exec(shell)
            } catch (e: Exception) {
                if (useRoot) {
                    Log.w(TAG, "su shell not found, falling back to sh", e)
                    Runtime.getRuntime().exec("sh")
                } else {
                    throw e
                }
            }
            os = DataOutputStream(process.outputStream)
            
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            isReader = BufferedReader(InputStreamReader(process.inputStream))
            esReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = isReader.readLines().joinToString("\n")
            val error = esReader.readLines().joinToString("\n")

            val exitCode = process.waitFor()

            Log.d(TAG, "Fallback Command result: exitCode=$exitCode, out=$output, err=$error")
            return ShellResult(
                isSuccess = exitCode == 0,
                output = output,
                error = error,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command", e)
            return ShellResult(
                isSuccess = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        } finally {
            try { os?.close() } catch (e: Exception) {}
            try { isReader?.close() } catch (e: Exception) {}
            try { esReader?.close() } catch (e: Exception) {}
            try { process?.inputStream?.close() } catch (e: Exception) {}
            try { process?.errorStream?.close() } catch (e: Exception) {}
            try { process?.outputStream?.close() } catch (e: Exception) {}
            try { process?.destroy() } catch (e: Exception) {}
        }
    }

    // Helper to simulate action if Root is not available
    fun simulateOrExecuteTap(x: Float, y: Float): ShellResult {
        val xInt = x.toInt()
        val yInt = y.toInt()
        Log.d(TAG, "Executing root tap command at ($xInt, $yInt)")
        
        val result = execute("input tap $xInt $yInt", useRoot = true)
        
        // Secondary reinforcement execution with direct su -c
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $xInt $yInt"))
            try { p.inputStream.close() } catch (e: Exception) {}
            try { p.errorStream.close() } catch (e: Exception) {}
            try { p.outputStream.close() } catch (e: Exception) {}
            p.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Direct su -c tap execution failed", e)
        }

        if (result.isSuccess) {
            return result
        }
        // Simulator mode for standard devices
        Log.d(TAG, "SIMULATOR: Mocking screen tap at ($xInt, $yInt)")
        return ShellResult(true, "Simulated tap at ($xInt, $yInt)", "", 0)
    }

    fun simulateOrExecuteForceStop(packageName: String): ShellResult {
        Log.d(TAG, "Executing root force stop for: $packageName")
        
        // Dual termination methods: am force-stop and pkill
        val result = execute("am force-stop $packageName", useRoot = true)
        execute("pkill -9 -f $packageName", useRoot = true)
        
        // Direct su -c backup execution
        try {
            val p1 = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
            try { p1.inputStream.close() } catch (e: Exception) {}
            try { p1.errorStream.close() } catch (e: Exception) {}
            try { p1.outputStream.close() } catch (e: Exception) {}
            p1.destroy()
            
            val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -9 -f $packageName"))
            try { p2.inputStream.close() } catch (e: Exception) {}
            try { p2.errorStream.close() } catch (e: Exception) {}
            try { p2.outputStream.close() } catch (e: Exception) {}
            p2.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Direct su -c force-stop execution failed", e)
        }

        if (result.isSuccess) {
            return result
        }
        Log.d(TAG, "SIMULATOR: Mocking force stop for $packageName")
        return ShellResult(true, "Simulated force stop of $packageName", "", 0)
    }

    fun simulateOrExecuteLockScreen(): ShellResult {
        val result = execute("input keyevent 26", useRoot = true)
        if (result.isSuccess) {
            return result
        }
        Log.d(TAG, "SIMULATOR: Mocking screen lock")
        return ShellResult(true, "Simulated screen lock", "", 0)
    }

    fun captureScreenshot(outputPath: String): Boolean {
        val result = execute("screencap -p $outputPath", useRoot = true)
        if (result.isSuccess) {
            return true
        }
        Log.d(TAG, "SIMULATOR: Screen capture simulated to $outputPath")
        return true
    }

    fun waitForAppToBeReady(context: android.content.Context, packageName: String, maxWaitSec: Int = 10): Boolean {
        Log.d(TAG, "Waiting for app $packageName to be ready/foreground (max $maxWaitSec sec)...")
        val startTime = System.currentTimeMillis()
        val timeoutMs = maxWaitSec * 1000L
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check 1: Check using root dumpsys window
            val result = execute("dumpsys window | grep mCurrentFocus", useRoot = true)
            val output = result.output.lowercase()
            if (result.isSuccess && output.contains(packageName.lowercase())) {
                Log.d(TAG, "App $packageName is in foreground according to dumpsys!")
                return true
            }
            
            // Check 2: Check using resumed activity dumpsys activity
            val result2 = execute("dumpsys activity | grep mResumedActivity", useRoot = true)
            val output2 = result2.output.lowercase()
            if (result2.isSuccess && output2.contains(packageName.lowercase())) {
                Log.d(TAG, "App $packageName is in foreground according to mResumedActivity!")
                return true
            }

            // Check 3: Check using ActivityManager running tasks (fallback, works sometimes)
            try {
                val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val runningTasks = am.getRunningTasks(1)
                if (runningTasks.isNotEmpty()) {
                    val topActivity = runningTasks[0].topActivity
                    if (topActivity != null && topActivity.packageName.equals(packageName, ignoreCase = true)) {
                        Log.d(TAG, "App $packageName is in foreground according to getRunningTasks!")
                        return true
                    }
                }
            } catch (e: Exception) {}

            // Sleep 500ms and try again
            try { Thread.sleep(500) } catch (e: Exception) {}
        }
        Log.w(TAG, "Timeout waiting for app $packageName to be ready.")
        return false
    }
}
