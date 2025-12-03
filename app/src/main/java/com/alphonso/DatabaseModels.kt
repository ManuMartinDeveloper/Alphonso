package com.alphonso

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class LogEventType {
    DETECTION, WARNING, APP_BLOCKED, AI_CANDIDATE, APP_RELEASED, FALSE_POSITIVE, RETRAINING, SERVICE_EVENT
}

@Entity(tableName = "event_logs")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val packageName: String,
    val details: String,
    var isFalsePositive: Boolean = false,
    val confidenceScore: Float = 0.0f
)

@Dao
interface EventLogDao {
    // FIX: Changed return type to Flow<List<...>> and removed 'suspend'.
    // This allows Room to notify the UI whenever data changes.
    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<EventLogEntity>>

    @Insert
    suspend fun insert(log: EventLogEntity)

    @Query("DELETE FROM event_logs")
    suspend fun clearAll()

    @Query("UPDATE event_logs SET isFalsePositive = 1 WHERE id = :id")
    suspend fun markAsFalsePositive(id: Int)

    @Query("UPDATE event_logs SET isFalsePositive = 0 WHERE id = :id")
    suspend fun unmarkAsFalsePositive(id: Int)
}

@Database(entities = [EventLogEntity::class], version = 1, exportSchema = false)
abstract class EventLogDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
}