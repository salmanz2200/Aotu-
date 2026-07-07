package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao, private val logDao: LogDao) {
    val allTasks: Flow<List<AutomationTask>> = taskDao.getAllTasksFlow()
    val allLogs: Flow<List<ExecutionLog>> = logDao.getAllLogsFlow()

    suspend fun getEnabledTasks(): List<AutomationTask> = taskDao.getEnabledTasks()

    suspend fun insertTask(task: AutomationTask): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: AutomationTask) = taskDao.updateTask(task)

    suspend fun deleteTask(task: AutomationTask) = taskDao.deleteTask(task)

    suspend fun getTaskById(id: Int): AutomationTask? = taskDao.getTaskById(id)

    suspend fun insertLog(log: ExecutionLog): Long = logDao.insertLog(log)

    suspend fun clearLogs() = logDao.clearLogs()
}
