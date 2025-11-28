package com.manumartin

import androidx.room.*

// 1. The Enum for Event Types
enum class LogEventType {
    DETECTION,      // AI found something
    WARNING,        // User warned
    APP_BLOCKED,    // Screen masked
    APP_RELEASED,   // User left app
    FALSE_POSITIVE, // User flagged mistake
    RETRAINING,     // System updated weights
    SERVICE_EVENT   // Service started/stopped
}

// 2. The Entity (Database Table)
@Entity(tableName = "event_logs")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // We store the Enum as a String
    val packageName: String,
    val details: String,
    val isFalsePositive: Boolean = false,
    val confidenceScore: Float = 0.0f
)

// 3. The DAO (Database Access Object)
@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<EventLogEntity>

    @Insert
    suspend fun insert(log: EventLogEntity)

    @Query("DELETE FROM event_logs")
    suspend fun clearAll()

    @Query("UPDATE event_logs SET isFalsePositive = 1 WHERE id = :id")
    suspend fun markAsFalsePositive(id: Int)

    // NEW: Allow undoing the flag
    @Query("UPDATE event_logs SET isFalsePositive = 0 WHERE id = :id")
    suspend fun unmarkFalsePositive(id: Int)
}

// 4. The Database Class
@Database(entities = [EventLogEntity::class], version = 1, exportSchema = false)
abstract class EventLogDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
}