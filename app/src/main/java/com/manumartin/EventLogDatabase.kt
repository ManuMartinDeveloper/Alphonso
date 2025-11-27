package com.manumartin

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val eventType: String,
    val packageName: String,
    val details: String,
    var isFalsePositive: Boolean
)

@Dao
interface EventLogDao {
    @Insert
    suspend fun insert(log: EventLogEntity)

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC")
    suspend fun getAll(): List<EventLogEntity>

    @Query("UPDATE event_log SET isFalsePositive = 1 WHERE id = :id")
    suspend fun flagAsFalsePositive(id: String)

    @Query("DELETE FROM event_log")
    suspend fun clearAll()
}

@Database(entities = [EventLogEntity::class], version = 1)
abstract class EventLogDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
}
