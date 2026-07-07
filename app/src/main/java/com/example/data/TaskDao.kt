package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM automation_tasks ORDER BY id DESC")
    fun getAllTasksFlow(): Flow<List<AutomationTask>>

    @Query("SELECT * FROM automation_tasks")
    suspend fun getAllTasksDirect(): List<AutomationTask>

    @Query("SELECT * FROM automation_tasks WHERE isEnabled = 1")
    suspend fun getEnabledTasks(): List<AutomationTask>

    @Query("SELECT * FROM automation_tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): AutomationTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AutomationTask): Long

    @Update
    suspend fun updateTask(task: AutomationTask)

    @Delete
    suspend fun deleteTask(task: AutomationTask)
}
