package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<ExecutionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLog): Long

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()
}
