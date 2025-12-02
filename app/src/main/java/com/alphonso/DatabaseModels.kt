package com.alphonso

import androidx.room.*

// 1. The Enum
enum class LogEventType {
    DETECTION,      // AI found something (High confidence - no longer used, replaced by WARNING)
    WARNING,        // User warned (Strike)
    APP_BLOCKED,    // App locked down
    AI_CANDIDATE,   // AI found something with low confidence (for logging only)
    APP_RELEASED,   // User left app (Not currently used)
    FALSE_POSITIVE, // User flagged mistake
    RETRAINING,     // System updated weights
    SERVICE_EVENT   // Service started/stopped
}

// 2. The Entity (Table)
@Entity(tableName = "event_logs")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // Store Enum as String
    val packageName: String,
    val details: String,
    var isFalsePositive: Boolean = false,
    val confidenceScore: Float = 0.0f
)

// 3. The DAO (Queries)
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

    @Query("UPDATE event_logs SET isFalsePositive = 0 WHERE id = :id")
    suspend fun unmarkAsFalsePositive(id: Int)
}

// 4. The Database
@Database(entities = [EventLogEntity::class], version = 1)
abstract class EventLogDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
}
