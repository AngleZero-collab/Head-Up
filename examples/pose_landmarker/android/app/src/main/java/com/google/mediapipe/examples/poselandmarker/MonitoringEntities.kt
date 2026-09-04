package com.google.mediapipe.examples.poselandmarker

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monitoring_sessions",
    indices = [Index("userId"), Index("startedAtMs"), Index("mode")],
)
data class MonitoringSessionEntity(
    @PrimaryKey val sessionId: String,
    val userId: String,
    val mode: String,
    val startedAtMs: Long,
    val startedElapsedRealtimeMs: Long,
    val endedAtMs: Long? = null,
    val timezone: String,
    val scoringVersion: Int,
    val eligibleForRanking: Boolean,
    val deviceSessionId: String,
    val finalState: String? = null,
    val actualInferenceFps: Float? = null,
    val batteryDeltaPercent: Int? = null,
    val peakThermalStatus: Int? = null,
    val peakMemoryMb: Int? = null,
)

@Entity(
    tableName = "posture_windows",
    indices = [Index("sessionId"), Index(value = ["userId", "startTimeMs"]), Index("isSynced")],
)
data class PostureWindowEntity(
    @PrimaryKey val windowId: String,
    val sessionId: String,
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val postureState: String,
    val averageConfidence: Float,
    val durationSeconds: Long,
    val greenSeconds: Long,
    val yellowSeconds: Long,
    val redSeconds: Long,
    val unknownSeconds: Long,
    val scoreDelta: Int,
    val challengePointsDelta: Int,
    val comboMultiplier: Double,
    val scoringVersion: Int,
    val mode: String,
    val isSynced: Boolean = false,
)

@Entity(
    tableName = "daily_posture_aggregates",
    indices = [Index(value = ["userId", "recordDate"]), Index("isSynced")],
)
data class DailyPostureAggregateEntity(
    @PrimaryKey val aggregateId: String,
    val userId: String,
    val recordDate: String,
    val mode: String,
    val greenSeconds: Long,
    val yellowSeconds: Long,
    val redSeconds: Long,
    val unknownSeconds: Long,
    val validSeconds: Long,
    val rawPoints: Int,
    val challengePoints: Int,
    val postureScore: Double?,
    val longestGreenStreakSeconds: Long,
    val greenStreakCount: Int,
    val greenStreakTotalSeconds: Long,
    val reminderCount: Int,
    val successfulCorrections: Int,
    val recoverySecondsTotal: Long,
    val averageRecoverySeconds: Double?,
    val observationSeconds: Long,
    val guardingSeconds: Long,
    val scoringVersion: Int,
    val updatedAtMs: Long,
    val idempotencyKey: String,
    val isSynced: Boolean = false,
    val syncedAtMs: Long? = null,
)

@Entity(
    tableName = "reminder_events",
    indices = [Index("sessionId"), Index(value = ["userId", "triggeredAtMs"]), Index("isSynced")],
)
data class ReminderEventEntity(
    @PrimaryKey val reminderId: String,
    val sessionId: String,
    val userId: String,
    val triggeredAtMs: Long,
    val postureState: String,
    val correctedAtMs: Long? = null,
    val recoverySeconds: Long? = null,
    val successfulCorrection: Boolean = false,
    val soundUsed: Boolean,
    val vibrationUsed: Boolean,
    val visualUsed: Boolean,
    val isSynced: Boolean = false,
)

@Entity(tableName = "education_profiles")
data class EducationProfileEntity(
    @PrimaryKey val userId: String,
    val countryCode: String,
    val schoolId: String?,
    val gradeCode: String?,
    val educationStage: String?,
    val publicAlias: String,
    val leaderboardOptIn: Boolean,
    val parentConsentStatus: String = "NOT_REQUIRED_OR_PENDING",
    val updatedAtMs: Long,
    val isSynced: Boolean = false,
)

@Entity(
    tableName = "schools",
    indices = [
        Index(value = ["countryCode", "educationStage"]),
        Index(value = ["region", "district"]),
        Index("officialSchoolCode", unique = true),
    ],
)
data class SchoolEntity(
    @PrimaryKey val schoolId: String,
    val countryCode: String,
    val officialSchoolCode: String,
    val schoolName: String,
    val localizedName: String,
    val educationStage: String,
    val region: String,
    val district: String,
    val activeStatus: String,
    val source: String,
    val sourceVersion: String,
    val updatedAtMs: Long,
    val verified: Boolean = true,
)

@Entity(tableName = "challenge_enrollments", indices = [Index("challengeCode")])
data class ChallengeEnrollmentEntity(
    @PrimaryKey val userId: String,
    val enrolled: Boolean,
    val challengeCode: String? = null,
    val joinedAtMs: Long? = null,
    val updatedAtMs: Long,
)

@Entity(tableName = "leaderboard_cache", indices = [Index("expiresAtMs")])
data class LeaderboardCacheEntity(
    @PrimaryKey val cacheKey: String,
    val entityType: String,
    val scopeType: String,
    val period: String,
    val queryJson: String,
    val payloadJson: String,
    val fetchedAtMs: Long,
    val expiresAtMs: Long,
)

@Entity(
    tableName = "sync_queue",
    indices = [Index("status"), Index("nextAttemptAtMs"), Index("idempotencyKey", unique = true)],
)
data class SyncQueueEntity(
    @PrimaryKey val queueId: String,
    val type: String,
    val entityId: String,
    val idempotencyKey: String,
    val payloadJson: String,
    val status: String = "PENDING",
    val attemptCount: Int = 0,
    val createdAtMs: Long,
    val nextAttemptAtMs: Long,
    val lastError: String? = null,
)

