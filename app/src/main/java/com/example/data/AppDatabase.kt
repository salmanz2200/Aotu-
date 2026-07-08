package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AutomationTask::class, ExecutionLog::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 → v2: added actionType, tapX, tapY, videoFilePath, mimicSteps columns;
        //           created the execution_logs table.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN actionType TEXT NOT NULL DEFAULT 'TAP'")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN tapX REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN tapY REAL NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN videoFilePath TEXT")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN mimicSteps TEXT")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS execution_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId INTEGER NOT NULL,
                        taskName TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        message TEXT NOT NULL,
                        screenshotPath TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        // v2 → v3: added lastRunTime, lastRunStatus columns.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN lastRunTime INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN lastRunStatus TEXT")
            }
        }

        // v3 → v4: added validation and advanced execution columns.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN validationType TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN validationText TEXT")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN validationWaitTimeSec INTEGER NOT NULL DEFAULT 6")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN loopUntilSuccess INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN maxAttempts INTEGER NOT NULL DEFAULT 3")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN referenceImagePath TEXT")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN delayBeforeTapSec INTEGER NOT NULL DEFAULT 3")
            }
        }

        // v4 → v5: added isRecurring, createdAt, updatedAt columns.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE automation_tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v5 → v6: added retryCount and maxRetries columns to support the retry mechanism.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE automation_tasks ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE automation_tasks ADD COLUMN maxRetries INTEGER NOT NULL DEFAULT 3"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "automator_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
