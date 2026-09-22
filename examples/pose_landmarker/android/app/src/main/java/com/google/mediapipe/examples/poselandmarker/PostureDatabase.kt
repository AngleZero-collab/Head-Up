package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "posture_records",
    indices = [
        Index(value = ["timestampMs"]),
        Index(value = ["isSynced", "timestampMs"]),
    ],
)
data class PostureRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String,
    val timestampMs: Long,
    val durationMs: Long,
    val angleDegrees: Int,
    val rawAngleDegrees: Float,
    val neckFlexionDegrees: Int,
    val shoulderBalanceDegrees: Int,
    val screenDistanceCm: Int?,
    val landmarkConfidence: Float,
    val zone: String,
    val source: String,
    val isRapidFall: Boolean = false,
    val isSynced: Boolean = false,
    val syncedAtMs: Long? = null,
)

@Dao
interface PostureRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: PostureRecordEntity)

    @Query("SELECT * FROM posture_records WHERE timestampMs >= :fromMs AND timestampMs < :toMs ORDER BY timestampMs ASC")
    fun recordsBetween(fromMs: Long, toMs: Long): List<PostureRecordEntity>

    @Query("SELECT * FROM posture_records WHERE isSynced = 0 ORDER BY timestampMs ASC LIMIT :limit")
    fun unsyncedRecords(limit: Int): List<PostureRecordEntity>

    @Query("SELECT COUNT(*) FROM posture_records WHERE isSynced = 0")
    fun unsyncedCount(): Int

    @Query("UPDATE posture_records SET isSynced = 1, syncedAtMs = :syncedAtMs WHERE id IN (:ids)")
    fun markSynced(ids: List<Long>, syncedAtMs: Long)

    @Query("DELETE FROM posture_records")
    fun deleteAll()

    @Query("DELETE FROM posture_records WHERE timestampMs < :cutoffMs AND isSynced = 1")
    fun deleteOlderThan(cutoffMs: Long)
}

@Database(
    entities = [
        PostureRecordEntity::class,
        MonitoringSessionEntity::class,
        PostureWindowEntity::class,
        DailyPostureAggregateEntity::class,
        ReminderEventEntity::class,
        EducationProfileEntity::class,
        SchoolEntity::class,
        ChallengeEnrollmentEntity::class,
        LeaderboardCacheEntity::class,
        SyncQueueEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class PostureDatabase : RoomDatabase() {
    abstract fun postureRecordDao(): PostureRecordDao
    abstract fun monitoringDao(): MonitoringDao

    companion object {
        private const val DATABASE_NAME = "headup-posture.db"

        @Volatile
        private var instance: PostureDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE posture_records ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-device'")
                db.execSQL("ALTER TABLE posture_records ADD COLUMN isRapidFall INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE posture_records ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE posture_records ADD COLUMN syncedAtMs INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_posture_records_isSynced_timestampMs ON posture_records(isSynced, timestampMs)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `monitoring_sessions` (`sessionId` TEXT NOT NULL, `userId` TEXT NOT NULL, `mode` TEXT NOT NULL, `startedAtMs` INTEGER NOT NULL, `startedElapsedRealtimeMs` INTEGER NOT NULL, `endedAtMs` INTEGER, `timezone` TEXT NOT NULL, `scoringVersion` INTEGER NOT NULL, `eligibleForRanking` INTEGER NOT NULL, `deviceSessionId` TEXT NOT NULL, `finalState` TEXT, `actualInferenceFps` REAL, `batteryDeltaPercent` INTEGER, `peakThermalStatus` INTEGER, `peakMemoryMb` INTEGER, PRIMARY KEY(`sessionId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monitoring_sessions_userId` ON `monitoring_sessions` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monitoring_sessions_startedAtMs` ON `monitoring_sessions` (`startedAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monitoring_sessions_mode` ON `monitoring_sessions` (`mode`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `posture_windows` (`windowId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `userId` TEXT NOT NULL, `startTimeMs` INTEGER NOT NULL, `endTimeMs` INTEGER NOT NULL, `postureState` TEXT NOT NULL, `averageConfidence` REAL NOT NULL, `durationSeconds` INTEGER NOT NULL, `greenSeconds` INTEGER NOT NULL, `yellowSeconds` INTEGER NOT NULL, `redSeconds` INTEGER NOT NULL, `unknownSeconds` INTEGER NOT NULL, `scoreDelta` INTEGER NOT NULL, `challengePointsDelta` INTEGER NOT NULL, `comboMultiplier` REAL NOT NULL, `scoringVersion` INTEGER NOT NULL, `mode` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`windowId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_posture_windows_sessionId` ON `posture_windows` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_posture_windows_userId_startTimeMs` ON `posture_windows` (`userId`, `startTimeMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_posture_windows_isSynced` ON `posture_windows` (`isSynced`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_posture_aggregates` (`aggregateId` TEXT NOT NULL, `userId` TEXT NOT NULL, `recordDate` TEXT NOT NULL, `mode` TEXT NOT NULL, `greenSeconds` INTEGER NOT NULL, `yellowSeconds` INTEGER NOT NULL, `redSeconds` INTEGER NOT NULL, `unknownSeconds` INTEGER NOT NULL, `validSeconds` INTEGER NOT NULL, `rawPoints` INTEGER NOT NULL, `challengePoints` INTEGER NOT NULL, `postureScore` REAL, `longestGreenStreakSeconds` INTEGER NOT NULL, `greenStreakCount` INTEGER NOT NULL, `greenStreakTotalSeconds` INTEGER NOT NULL, `reminderCount` INTEGER NOT NULL, `successfulCorrections` INTEGER NOT NULL, `recoverySecondsTotal` INTEGER NOT NULL, `averageRecoverySeconds` REAL, `observationSeconds` INTEGER NOT NULL, `guardingSeconds` INTEGER NOT NULL, `scoringVersion` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, `idempotencyKey` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, `syncedAtMs` INTEGER, PRIMARY KEY(`aggregateId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_posture_aggregates_userId_recordDate` ON `daily_posture_aggregates` (`userId`, `recordDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_posture_aggregates_isSynced` ON `daily_posture_aggregates` (`isSynced`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_events` (`reminderId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `userId` TEXT NOT NULL, `triggeredAtMs` INTEGER NOT NULL, `postureState` TEXT NOT NULL, `correctedAtMs` INTEGER, `recoverySeconds` INTEGER, `successfulCorrection` INTEGER NOT NULL, `soundUsed` INTEGER NOT NULL, `vibrationUsed` INTEGER NOT NULL, `visualUsed` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`reminderId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_events_sessionId` ON `reminder_events` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_events_userId_triggeredAtMs` ON `reminder_events` (`userId`, `triggeredAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_events_isSynced` ON `reminder_events` (`isSynced`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `education_profiles` (`userId` TEXT NOT NULL, `countryCode` TEXT NOT NULL, `schoolId` TEXT, `gradeCode` TEXT, `educationStage` TEXT, `publicAlias` TEXT NOT NULL, `leaderboardOptIn` INTEGER NOT NULL, `parentConsentStatus` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `schools` (`schoolId` TEXT NOT NULL, `countryCode` TEXT NOT NULL, `officialSchoolCode` TEXT NOT NULL, `schoolName` TEXT NOT NULL, `localizedName` TEXT NOT NULL, `educationStage` TEXT NOT NULL, `region` TEXT NOT NULL, `district` TEXT NOT NULL, `activeStatus` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceVersion` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `verified` INTEGER NOT NULL, PRIMARY KEY(`schoolId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_schools_countryCode_educationStage` ON `schools` (`countryCode`, `educationStage`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_schools_region_district` ON `schools` (`region`, `district`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_schools_officialSchoolCode` ON `schools` (`officialSchoolCode`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `challenge_enrollments` (`userId` TEXT NOT NULL, `enrolled` INTEGER NOT NULL, `challengeCode` TEXT, `joinedAtMs` INTEGER, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_challenge_enrollments_challengeCode` ON `challenge_enrollments` (`challengeCode`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `leaderboard_cache` (`cacheKey` TEXT NOT NULL, `entityType` TEXT NOT NULL, `scopeType` TEXT NOT NULL, `period` TEXT NOT NULL, `queryJson` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `fetchedAtMs` INTEGER NOT NULL, `expiresAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_leaderboard_cache_expiresAtMs` ON `leaderboard_cache` (`expiresAtMs`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`queueId` TEXT NOT NULL, `type` TEXT NOT NULL, `entityId` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, `nextAttemptAtMs` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`queueId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_status` ON `sync_queue` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_nextAttemptAtMs` ON `sync_queue` (`nextAttemptAtMs`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_queue_idempotencyKey` ON `sync_queue` (`idempotencyKey`)")
            }
        }

        fun getInstance(context: Context): PostureDatabase = instance ?: synchronized(this) {
            instance ?: buildDatabase(context.applicationContext).also { instance = it }
        }

        fun databaseFile(context: Context) = context.applicationContext.getDatabasePath(DATABASE_NAME)

        private fun buildDatabase(context: Context): PostureDatabase =
            Room.databaseBuilder(context, PostureDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(HeadUpDatabasePassphrase.createSupportFactory(context, DATABASE_NAME))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
