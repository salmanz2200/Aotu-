package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_logs")
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val taskName: String,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "SUCCESS" or "FAILED"
    val message: String,
    val screenshotPath: String? = null
)
