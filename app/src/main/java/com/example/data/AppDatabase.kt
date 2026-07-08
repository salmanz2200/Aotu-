package com.example.data

import android.content.Context
import android.util.Log
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
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Safe migration definitions from version 1 to 6
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSchema(db)
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSchema(db)
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSchema(db)
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSchema(db)
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSchema(db)
            }
        }

        private fun migrateSchema(db: SupportSQLiteDatabase) {
            Log.d(TAG, "Running schema migration checks...")
            try {
                // Ensure all possible columns added in newer versions exist in the automation_tasks table
                addColumnIfNotExists(db, "automation_tasks", "actionType", "TEXT", "'TAP'")
                addColumnIfNotExists(db, "automation_tasks", "tapX", "REAL", "0.0")
                addColumnIfNotExists(db, "automation_tasks", "tapY", "REAL", "0.0")
                addColumnIfNotExists(db, "automation_tasks", "videoFilePath", "TEXT", "NULL")
                addColumnIfNotExists(db, "automation_tasks", "mimicSteps", "TEXT", "NULL")
                addColumnIfNotExists(db, "automation_tasks", "lastRunTime", "INTEGER", "0")
                addColumnIfNotExists(db, "automation_tasks", "lastRunStatus", "TEXT", "NULL")
                addColumnIfNotExists(db, "automation_tasks", "validationType", "TEXT", "'NONE'")
                addColumnIfNotExists(db, "automation_tasks", "validationText", "TEXT", "NULL")
                addColumnIfNotExists(db, "automation_tasks", "validationWaitTimeSec", "INTEGER", "6")
                addColumnIfNotExists(db, "automation_tasks", "loopUntilSuccess", "INTEGER", "0")
                addColumnIfNotExists(db, "automation_tasks", "maxAttempts", "INTEGER", "3")
                addColumnIfNotExists(db, "automation_tasks", "referenceImagePath", "TEXT", "NULL")
                addColumnIfNotExists(db, "automation_tasks", "delayBeforeTapSec", "INTEGER", "3")
                addColumnIfNotExists(db, "automation_tasks", "isRecurring", "INTEGER", "1")
                addColumnIfNotExists(db, "automation_tasks", "createdAt", "INTEGER", "0")
                addColumnIfNotExists(db, "automation_tasks", "updatedAt", "INTEGER", "0")
                addColumnIfNotExists(db, "automation_tasks", "retryCount", "INTEGER", "0")
                addColumnIfNotExists(db, "automation_tasks", "maxRetries", "INTEGER", "3")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing migration schema upgrades", e)
            }
        }

        private fun addColumnIfNotExists(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnType: String,
            defaultValue: String? = null
        ) {
            var columnExists = false
            val cursor = db.query("PRAGMA table_info($tableName)")
            try {
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                            columnExists = true
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking column existence for $columnName in table $tableName", e)
            } finally {
                cursor.close()
            }

            if (!columnExists) {
                val defaultClause = if (defaultValue != null) " DEFAULT $defaultValue" else ""
                db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnType$defaultClause")
                Log.d(TAG, "✅ Added missing column: $columnName to table: $tableName")
            } else {
                Log.d(TAG, "ℹ️ Column $columnName already exists in table $tableName")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "automator_database"
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                .fallbackToDestructiveMigrationOnDowngrade() // Prevent data loss on upgrades, fallback only on downgrade
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
