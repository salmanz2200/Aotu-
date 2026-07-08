package com.example.utils

import android.util.Log
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object ShellExecutor {
    private const val TAG = "ShellExecutor"
    private var isRootAvailableCached: Boolean? = null
    private val packageNameRegex = Regex("^[a-zA-Z0-9_.]+$")
    private val classNameRegex = Regex("^[a-zA-Z0-9_.$]+$")

    private fun readProcessStreams(process: Process): Pair<String, String> {
        var output = ""
        var error = ""
        val t1 = Thread {
            try {
                output = process.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading stdout", e)
            }
        }
        val t2 = Thread {
            try {
                error = process.errorStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading stderr", e)
            }
        }
        t1.start()
        t2.start()
        try { t1.join(10000) } catch (e: Exception) {}
        try { t2.join(10000) } catch (e: Exception) {}
        return Pair(output.trim(), error.trim())
    }

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
            try {
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val (output, error) = readProcessStreams(process)
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
                try { process?.inputStream?.close() } catch (e: Exception) {}
                try { process?.errorStream?.close() } catch (e: Exception) {}
                try { process?.outputStream?.close() } catch (e: Exception) {}
                try { process?.destroy() } catch (e: Exception) {}
            }
        }

        // Fallback to the original stdin writing shell execution or standard shell
        var process: Process? = null
        var os: DataOutputStream? = null

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

            val (output, error) = readProcessStreams(process)
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
        
        if (result.isSuccess) {
            return result
        }
        // Simulator mode for standard devices
        Log.d(TAG, "SIMULATOR: Mocking screen tap at ($xInt, $yInt)")
        return ShellResult(true, "Simulated tap at ($xInt, $yInt)", "", 0)
    }

    fun startActivityComponent(packageName: String, className: String): ShellResult {
        if (!packageName.matches(packageNameRegex) || !className.matches(classNameRegex)) {
            Log.e(TAG, "Rejected unsafe component name: $packageName/$className")
            return ShellResult(false, "", "Invalid activity component name", -1)
        }

        val component = "$packageName/$className"
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am start -n $component"))
            val (output, error) = readProcessStreams(process)
            val exitCode = process.waitFor()
            Log.d(TAG, "am start result: exitCode=$exitCode, out=$output, err=$error")
            ShellResult(
                isSuccess = exitCode == 0,
                output = output,
                error = error,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start component with am", e)
            ShellResult(
                isSuccess = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        } finally {
            try { process?.inputStream?.close() } catch (e: Exception) {}
            try { process?.errorStream?.close() } catch (e: Exception) {}
            try { process?.outputStream?.close() } catch (e: Exception) {}
            try { process?.destroy() } catch (e: Exception) {}
        }
    }

    private fun sanitizePackageName(packageName: String): String {
        // Android package names contain only alphanumeric characters, underscores, and dots.
        // Enforce a strict regex to prevent any shell command injection (no ;, |, &, `, \, etc.)
        val regex = Regex("^[a-zA-Z0-9_.]+$")
        if (!regex.matches(packageName)) {
            Log.e(TAG, "❌ SECURITY ALERT: Potential command injection blocked in package name: '$packageName'")
            throw IllegalArgumentException("Invalid package name: potential command injection")
        }
        return packageName
    }

    fun simulateOrExecuteForceStop(packageName: String): ShellResult {
        val safePackageName = sanitizePackageName(packageName)
        Log.d(TAG, "Executing root force stop for: $safePackageName")
        
        val result = execute("am force-stop $safePackageName", useRoot = true)
        
        if (result.isSuccess) {
            return result
        }
        Log.d(TAG, "SIMULATOR: Mocking force stop for $safePackageName")
        return ShellResult(true, "Simulated force stop of $safePackageName", "", 0)
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
        return result.isSuccess
    }

    suspend fun waitForAppToBeReady(context: android.content.Context, packageName: String, maxWaitSec: Int = 10): Boolean {
        val safePackageName = sanitizePackageName(packageName)
        Log.d(TAG, "Waiting for app $safePackageName to be ready/foreground (max $maxWaitSec sec)...")
        val startTime = System.currentTimeMillis()
        val timeoutMs = maxWaitSec * 1000L
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check 1: Check using root dumpsys window
            val result = execute("dumpsys window | grep mCurrentFocus", useRoot = true)
            val output = result.output.lowercase()
            if (result.isSuccess && output.contains(safePackageName.lowercase())) {
                Log.d(TAG, "App $safePackageName is in foreground according to dumpsys!")
                return true
            }
            
            // Check 2: Check using resumed activity dumpsys activity
            val result2 = execute("dumpsys activity | grep mResumedActivity", useRoot = true)
            val output2 = result2.output.lowercase()
            if (result2.isSuccess && output2.contains(safePackageName.lowercase())) {
                Log.d(TAG, "App $safePackageName is in foreground according to mResumedActivity!")
                return true
            }

            // Sleep 500ms and try again
            try { delay(500) } catch (e: Exception) {}
        }
        Log.w(TAG, "Timeout waiting for app $safePackageName to be ready.")
        return false
    }
}
