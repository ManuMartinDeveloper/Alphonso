package com.alphonso

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

enum class MoralCategory(val displayName: String, val virtueFocus: String) {
    PURITY_CHASTITY("Purity & Chastity", "Custody of the Eyes (Matt 5:8)"),
    TEMPERANCE("Temperance & Moderation", "Digital Freedom & Sobriety (1 Cor 6:19)"),
    CHARITY("Charity & Christian Love", "Brotherly Kindness & Pure Speech (Eph 4:29)"),
    PIETY_TRUTH("Piety & Truth", "Spiritual Armor & Holy Reverence (Eph 6:11)"),
    ANTI_TAMPER("Anti-Tamper Shield", "Protected Device Integrity"),
    SYSTEM_GUARD("System Conscience", "Sentinel Watchdog")
}

enum class SensitivityProfile(val displayName: String, val ageGroup: String, val description: String) {
    STRICT_CHILD("Strict Child", "Ages 5–12", "Maximum sensitivity, full custody of eyes, safe-search enforcement"),
    BALANCED_YOUTH("Balanced Youth", "Ages 13–17", "Educational leeway, moderate social filtering, complete adult block"),
    MATURE_CONSCIENCE("Mature Conscience", "Adult / Self", "Custom threshold monitoring, accountability logs, prayer alerts")
}

enum class LogEventType {
    DETECTION, WARNING, APP_BLOCKED, AI_CANDIDATE, APP_RELEASED, FALSE_POSITIVE, RETRAINING, SERVICE_EVENT, TAMPER_ATTEMPT
}

// 1. Incident Log Entity
@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = MoralCategory.PURITY_CHASTITY.name,
    val packageName: String,
    val appName: String,
    val triggerSnippet: String,
    val confidenceScore: Float = 1.0f,
    val actionTaken: String = "Virtue Shield Overlay",
    var isFalsePositive: Boolean = false,
    val detourScripture: String = "Matthew 5:8"
)

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE category = :category ORDER BY timestamp DESC")
    fun getIncidentsByCategory(category: String): Flow<List<IncidentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: IncidentEntity)

    @Query("UPDATE incidents SET isFalsePositive = :isFalse WHERE id = :id")
    suspend fun setFalsePositive(id: Int, isFalse: Boolean)

    @Query("DELETE FROM incidents")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM incidents")
    fun getCount(): Flow<Int>
}

// 2. Daily Faith Quest Entity
@Entity(tableName = "faith_quests")
data class FaithQuestEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val questKey: String,
    val title: String,
    val description: String,
    val category: String, // PRAYER, TEMPERANCE, CHARITY, KNOWLEDGE
    val virtuePoints: Int = 20,
    val isCompleted: Boolean = false,
    val completedTimestamp: Long? = null,
    val streakDays: Int = 0
)

@Dao
interface FaithQuestDao {
    @Query("SELECT * FROM faith_quests ORDER BY id ASC")
    fun getAllQuests(): Flow<List<FaithQuestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quests: List<FaithQuestEntity>)

    @Update
    suspend fun update(quest: FaithQuestEntity)

    @Query("UPDATE faith_quests SET isCompleted = :completed, completedTimestamp = :timestamp, streakDays = streakDays + 1 WHERE id = :id")
    suspend fun setQuestCompleted(id: Int, completed: Boolean, timestamp: Long)

    @Query("UPDATE faith_quests SET isCompleted = 0 WHERE isCompleted = 1")
    suspend fun resetDailyQuests()
}

// 3. Soul's Virtue Garden Entity
@Entity(tableName = "virtue_garden")
data class VirtueGardenEntity(
    @PrimaryKey val id: Int = 1,
    val virtueLevel: Int = 1, // 1=Seedling of Grace, 2=Sprout of Charity, 3=Blossom of Purity, 4=Golden Tree of Divine Light
    val stageName: String = "Seedling of Grace",
    val totalVirtuePoints: Int = 45,
    val waterCount: Int = 3,
    val lastWateredTimestamp: Long = System.currentTimeMillis(),
    val treeHealthPercent: Int = 100
)

@Dao
interface VirtueGardenDao {
    @Query("SELECT * FROM virtue_garden WHERE id = 1")
    fun getGarden(): Flow<VirtueGardenEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(garden: VirtueGardenEntity)

    @Query("UPDATE virtue_garden SET totalVirtuePoints = totalVirtuePoints + :points, waterCount = waterCount + 1, lastWateredTimestamp = :time WHERE id = 1")
    suspend fun addWaterPoints(points: Int, time: Long)

    @Query("UPDATE virtue_garden SET virtueLevel = :level, stageName = :stageName WHERE id = 1")
    suspend fun updateLevel(level: Int, stageName: String)
}

// 4. Moral Filter Rule Entity
@Entity(tableName = "moral_rules")
data class MoralRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // PURITY_CHASTITY, TEMPERANCE, CHARITY, PIETY_TRUTH
    val keywordOrPattern: String,
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = true
)

@Dao
interface MoralRuleDao {
    @Query("SELECT * FROM moral_rules ORDER BY category ASC, id ASC")
    fun getAllRules(): Flow<List<MoralRuleEntity>>

    @Query("SELECT * FROM moral_rules WHERE isEnabled = 1")
    suspend fun getActiveRulesList(): List<MoralRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: MoralRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MoralRuleEntity>)

    @Update
    suspend fun update(rule: MoralRuleEntity)

    @Delete
    suspend fun delete(rule: MoralRuleEntity)
}

// 5. Daily Catholic Examen Journal
@Entity(tableName = "daily_examens")
data class DailyExamenEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateString: String,
    val timestamp: Long = System.currentTimeMillis(),
    val gratitudeNote: String,
    val momentsOfGrace: String,
    val challengesFaced: String,
    val resolutionTomorrow: String,
    val peaceRating: Int = 5 // 1 to 5
)

@Dao
interface DailyExamenDao {
    @Query("SELECT * FROM daily_examens ORDER BY timestamp DESC")
    fun getAllExamens(): Flow<List<DailyExamenEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(examen: DailyExamenEntity)
}

// 6. Settings Entity
@Entity(tableName = "sanctuary_settings")
data class SanctuarySettingsEntity(
    @PrimaryKey val id: Int = 1,
    val hashedParentPin: String = "1234",
    val sensitivityProfile: String = SensitivityProfile.BALANCED_YOUTH.name,
    val scanIntervalMs: Long = 800L, // 500, 800, 1500
    val antiTamperEnabled: Boolean = true,
    val custodyOfEyesEnabled: Boolean = true,
    val holyLockscreenEnabled: Boolean = true,
    val doubleTapToSleepEnabled: Boolean = true,
    val ttsPrayersEnabled: Boolean = true,
    val activePrayerName: String = "Hail Mary"
)

@Dao
interface SanctuarySettingsDao {
    @Query("SELECT * FROM sanctuary_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<SanctuarySettingsEntity?>

    @Query("SELECT * FROM sanctuary_settings WHERE id = 1")
    suspend fun getSettings(): SanctuarySettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: SanctuarySettingsEntity)

    @Update
    suspend fun update(settings: SanctuarySettingsEntity)
}

// Legacy event log for backward compatibility
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

@Database(
    entities = [
        IncidentEntity::class,
        FaithQuestEntity::class,
        VirtueGardenEntity::class,
        MoralRuleEntity::class,
        DailyExamenEntity::class,
        SanctuarySettingsEntity::class,
        EventLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SanctuaryDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun faithQuestDao(): FaithQuestDao
    abstract fun virtueGardenDao(): VirtueGardenDao
    abstract fun moralRuleDao(): MoralRuleDao
    abstract fun dailyExamenDao(): DailyExamenDao
    abstract fun sanctuarySettingsDao(): SanctuarySettingsDao
    abstract fun eventLogDao(): EventLogDao
}

// Legacy alias
typealias EventLogDatabase = SanctuaryDatabase

object SanctuaryDatabaseProvider {
    @Volatile
    private var INSTANCE: SanctuaryDatabase? = null

    fun getDatabase(context: Context): SanctuaryDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                SanctuaryDatabase::class.java,
                "sanctuary-database"
            ).fallbackToDestructiveMigration()
            .build()
            INSTANCE = instance
            seedInitialDataIfNeeded(instance)
            instance
        }
    }

    private fun seedInitialDataIfNeeded(db: SanctuaryDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            // Seed settings if empty
            if (db.sanctuarySettingsDao().getSettings() == null) {
                db.sanctuarySettingsDao().insert(SanctuarySettingsEntity())
            }

            // Seed initial garden if empty
            db.virtueGardenDao().insertOrUpdate(
                VirtueGardenEntity(
                    id = 1,
                    virtueLevel = 2,
                    stageName = "Sprout of Charity",
                    totalVirtuePoints = 145,
                    waterCount = 8,
                    lastWateredTimestamp = System.currentTimeMillis()
                )
            )

            // Seed initial daily faith quests
            db.faithQuestDao().insertAll(
                listOf(
                    FaithQuestEntity(
                        questKey = "morning_offering",
                        title = "Morning Offering of the Heart",
                        description = "Offer your thoughts, words, and screen time to the Sacred Heart before browsing.",
                        category = "PRAYER",
                        virtuePoints = 25,
                        isCompleted = true,
                        streakDays = 5
                    ),
                    FaithQuestEntity(
                        questKey = "screen_fast",
                        title = "1-Hour Digital Fast for Jesus",
                        description = "Set down all screens for one peaceful hour of silence, reading, or family work.",
                        category = "TEMPERANCE",
                        virtuePoints = 35,
                        isCompleted = false,
                        streakDays = 3
                    ),
                    FaithQuestEntity(
                        questKey = "act_of_kindness",
                        title = "Secret Act of Kindness",
                        description = "Perform a hidden chore or speak a loving blessing to someone in your household.",
                        category = "CHARITY",
                        virtuePoints = 30,
                        isCompleted = false,
                        streakDays = 4
                    ),
                    FaithQuestEntity(
                        questKey = "rosary_decade",
                        title = "Family Rosary Decade",
                        description = "Pray one decade of the Holy Rosary with custody of mind and reverence.",
                        category = "PRAYER",
                        virtuePoints = 40,
                        isCompleted = false,
                        streakDays = 7
                    ),
                    FaithQuestEntity(
                        questKey = "saint_hero_reading",
                        title = "Saint Hero Reflection",
                        description = "Read the life story of St. Carlo Acutis or St. Dominic Savio in the Faith Hub.",
                        category = "KNOWLEDGE",
                        virtuePoints = 20,
                        isCompleted = false,
                        streakDays = 2
                    )
                )
            )

            // Seed initial Catholic moral rules
            db.moralRuleDao().insertAll(
                listOf(
                    // Purity & Chastity
                    MoralRuleEntity(category = MoralCategory.PURITY_CHASTITY.name, keywordOrPattern = "pornhub"),
                    MoralRuleEntity(category = MoralCategory.PURITY_CHASTITY.name, keywordOrPattern = "xvideos"),
                    MoralRuleEntity(category = MoralCategory.PURITY_CHASTITY.name, keywordOrPattern = "xnxx"),
                    MoralRuleEntity(category = MoralCategory.PURITY_CHASTITY.name, keywordOrPattern = "onlyfans"),
                    MoralRuleEntity(category = MoralCategory.PURITY_CHASTITY.name, keywordOrPattern = "erotic"),
                    MoralRuleEntity(category = MoralCategory.PURITY_CHASTITY.name, keywordOrPattern = "nsfw"),
                    MoralRuleEntity(category = MoralCategory.PURITY_CHASTITY.name, keywordOrPattern = "hentai"),
                    MoralRuleEntity(category = MoralCategory.PURITY_CHASTITY.name, keywordOrPattern = "striptease"),
                    // Temperance
                    MoralRuleEntity(category = MoralCategory.TEMPERANCE.name, keywordOrPattern = "online casino"),
                    MoralRuleEntity(category = MoralCategory.TEMPERANCE.name, keywordOrPattern = "sports betting"),
                    MoralRuleEntity(category = MoralCategory.TEMPERANCE.name, keywordOrPattern = "stake.com"),
                    MoralRuleEntity(category = MoralCategory.TEMPERANCE.name, keywordOrPattern = "draftkings"),
                    MoralRuleEntity(category = MoralCategory.TEMPERANCE.name, keywordOrPattern = "vape shop"),
                    // Charity
                    MoralRuleEntity(category = MoralCategory.CHARITY.name, keywordOrPattern = "kill yourself"),
                    MoralRuleEntity(category = MoralCategory.CHARITY.name, keywordOrPattern = "hate speech"),
                    MoralRuleEntity(category = MoralCategory.CHARITY.name, keywordOrPattern = "cyberbully"),
                    // Piety & Truth
                    MoralRuleEntity(category = MoralCategory.PIETY_TRUTH.name, keywordOrPattern = "satanic ritual"),
                    MoralRuleEntity(category = MoralCategory.PIETY_TRUTH.name, keywordOrPattern = "ouija board"),
                    MoralRuleEntity(category = MoralCategory.PIETY_TRUTH.name, keywordOrPattern = "black magic occult")
                )
            )
        }
    }
}