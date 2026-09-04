package com.google.mediapipe.examples.poselandmarker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MonitoringDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSession(session: MonitoringSessionEntity)

    @Query("SELECT * FROM monitoring_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun session(sessionId: String): MonitoringSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWindow(window: PostureWindowEntity)

    @Query("SELECT * FROM posture_windows WHERE sessionId = :sessionId ORDER BY startTimeMs")
    fun windowsForSession(sessionId: String): List<PostureWindowEntity>

    @Query("SELECT * FROM posture_windows WHERE isSynced = 0 ORDER BY startTimeMs LIMIT :limit")
    fun unsyncedWindows(limit: Int): List<PostureWindowEntity>

    @Query("UPDATE posture_windows SET isSynced = 1 WHERE windowId IN (:windowIds)")
    fun markWindowsSynced(windowIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDailyAggregate(aggregate: DailyPostureAggregateEntity)

    @Query("SELECT * FROM daily_posture_aggregates WHERE aggregateId = :aggregateId LIMIT 1")
    fun dailyAggregate(aggregateId: String): DailyPostureAggregateEntity?

    @Query("SELECT * FROM daily_posture_aggregates WHERE userId = :userId AND recordDate >= :fromDate AND recordDate <= :toDate ORDER BY recordDate, mode")
    fun aggregatesBetween(userId: String, fromDate: String, toDate: String): List<DailyPostureAggregateEntity>

    @Query("SELECT * FROM daily_posture_aggregates WHERE isSynced = 0 ORDER BY recordDate LIMIT :limit")
    fun unsyncedAggregates(limit: Int): List<DailyPostureAggregateEntity>

    @Query("UPDATE daily_posture_aggregates SET isSynced = 1, syncedAtMs = :syncedAtMs WHERE aggregateId IN (:aggregateIds)")
    fun markAggregatesSynced(aggregateIds: List<String>, syncedAtMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReminder(reminder: ReminderEventEntity)

    @Update
    fun updateReminder(reminder: ReminderEventEntity)

    @Query("SELECT * FROM reminder_events WHERE reminderId = :reminderId LIMIT 1")
    fun reminder(reminderId: String): ReminderEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertEducationProfile(profile: EducationProfileEntity)

    @Query("SELECT * FROM education_profiles WHERE userId = :userId LIMIT 1")
    fun educationProfile(userId: String): EducationProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSchools(schools: List<SchoolEntity>)

    @Query("SELECT * FROM schools WHERE countryCode = :countryCode AND activeStatus != 'CLOSED' AND (:stage IS NULL OR educationStage = :stage) AND (:query = '' OR schoolName LIKE '%' || :query || '%' OR localizedName LIKE '%' || :query || '%' OR officialSchoolCode LIKE '%' || :query || '%') ORDER BY region, district, localizedName LIMIT :limit OFFSET :offset")
    fun searchSchools(countryCode: String, stage: String?, query: String, limit: Int, offset: Int): List<SchoolEntity>

    @Query("SELECT * FROM schools WHERE schoolId = :schoolId LIMIT 1")
    fun school(schoolId: String): SchoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertEnrollment(enrollment: ChallengeEnrollmentEntity)

    @Query("SELECT * FROM challenge_enrollments WHERE userId = :userId LIMIT 1")
    fun enrollment(userId: String): ChallengeEnrollmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertLeaderboardCache(cache: LeaderboardCacheEntity)

    @Query("SELECT * FROM leaderboard_cache WHERE cacheKey = :cacheKey LIMIT 1")
    fun leaderboardCache(cacheKey: String): LeaderboardCacheEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun enqueueSync(item: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' AND nextAttemptAtMs <= :nowMs ORDER BY createdAtMs LIMIT :limit")
    fun pendingSync(nowMs: Long, limit: Int): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE queueId IN (:queueIds)")
    fun removeSyncItems(queueIds: List<String>)

    @Query("DELETE FROM monitoring_sessions")
    fun deleteSessions()

    @Query("DELETE FROM posture_windows")
    fun deleteWindows()

    @Query("DELETE FROM daily_posture_aggregates")
    fun deleteAggregates()

    @Query("DELETE FROM reminder_events")
    fun deleteReminders()

    @Query("DELETE FROM education_profiles")
    fun deleteEducationProfiles()

    @Query("DELETE FROM challenge_enrollments")
    fun deleteEnrollments()

    @Query("DELETE FROM leaderboard_cache")
    fun deleteLeaderboardCache()

    @Query("DELETE FROM sync_queue")
    fun deleteSyncQueue()
}

