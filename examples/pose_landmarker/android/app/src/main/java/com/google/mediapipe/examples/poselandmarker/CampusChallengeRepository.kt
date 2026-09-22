package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CampusChallengeSummary(
    val challengePoints: Int,
    val postureScore: Double?,
    val validDays: Int,
    val validMinutes: Long,
    val longestGreenStreakSeconds: Long,
    val qualified: Boolean,
)

data class CachedLeaderboardResult(
    val response: CampusLeaderboardResponse?,
    val fromCache: Boolean,
    val cachedAtMs: Long? = null,
)

object CampusChallengeRepository {
    private val gson = Gson()

    suspend fun educationProfile(context: Context): EducationProfileEntity? = withContext(Dispatchers.IO) {
        PostureDatabase.getInstance(context.applicationContext).monitoringDao()
            .educationProfile(HeadUpAuthStore.currentUserId(context))
    }

    suspend fun saveEducationProfile(context: Context, profile: EducationProfileEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val dao = PostureDatabase.getInstance(appContext).monitoringDao()
            dao.upsertEducationProfile(profile.copy(isSynced = false))
            HeadUpRepository.setLeaderboardOptIn(appContext, profile.leaderboardOptIn)
            dao.upsertEnrollment(
                ChallengeEnrollmentEntity(
                    userId = profile.userId,
                    enrolled = profile.leaderboardOptIn,
                    challengeCode = "campus-posture-v1",
                    joinedAtMs = if (profile.leaderboardOptIn) System.currentTimeMillis() else null,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
            if (!HeadUpAuthStore.isSignedIn(appContext)) return@withContext Result.success(Unit)
            runCatching {
                val response = HeadUpApiClient.authenticatedService(appContext).updateEducationProfile(
                    EducationProfileRequest(
                        countryCode = profile.countryCode,
                        schoolId = profile.schoolId,
                        gradeCode = profile.gradeCode,
                        educationStage = profile.educationStage,
                        publicAlias = profile.publicAlias,
                        leaderboardOptIn = profile.leaderboardOptIn,
                        parentConsentStatus = profile.parentConsentStatus,
                    ),
                )
                dao.upsertEducationProfile(profile.copy(isSynced = true, updatedAtMs = System.currentTimeMillis()))
                response
            }.map { Unit }
        }

    suspend fun searchSchools(
        context: Context,
        stage: String?,
        query: String,
    ): List<SchoolEntity> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val dao = PostureDatabase.getInstance(appContext).monitoringDao()
        val local = dao.searchSchools("TW", stage, query.trim(), 30, 0)
        if (local.isNotEmpty() || !HeadUpAuthStore.isSignedIn(appContext)) return@withContext local
        runCatching {
            HeadUpApiClient.authenticatedService(appContext).schools(
                countryCode = "TW",
                stage = stage,
                query = query.trim(),
            ).map { school ->
                SchoolEntity(
                    schoolId = school.id,
                    countryCode = "TW",
                    officialSchoolCode = school.officialSchoolCode,
                    schoolName = school.localizedName,
                    localizedName = school.localizedName,
                    educationStage = school.educationStage,
                    region = school.region,
                    district = school.district,
                    activeStatus = "ACTIVE",
                    source = school.source,
                    sourceVersion = "server",
                    updatedAtMs = System.currentTimeMillis(),
                    verified = school.verified,
                )
            }.also { if (it.isNotEmpty()) dao.upsertSchools(it) }
        }.getOrDefault(local)
    }

    suspend fun school(context: Context, schoolId: String?): SchoolEntity? = withContext(Dispatchers.IO) {
        schoolId?.let {
            PostureDatabase.getInstance(context.applicationContext).monitoringDao().school(it)
        }
    }

    suspend fun challengeSummary(context: Context, period: LeaderboardPeriod): CampusChallengeSummary =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val (from, to) = periodRange(period)
            val rows = PostureDatabase.getInstance(appContext).monitoringDao()
                .aggregatesBetween(HeadUpAuthStore.currentUserId(appContext), from, to)
                .filter { it.mode == MonitoringMode.GUARDING.name }
            val green = rows.sumOf { it.greenSeconds }
            val yellow = rows.sumOf { it.yellowSeconds }
            val red = rows.sumOf { it.redSeconds }
            val validSeconds = green + yellow + red
            val validDays = rows.count { it.validSeconds > 0L }
            val score = PostureScoreCalculator.calculate(green, yellow, red)
            val qualification = LeaderboardQualification(
                validDays = validDays,
                validSeconds = validSeconds,
                longestGreenStreakSeconds = rows.maxOfOrNull { it.longestGreenStreakSeconds } ?: 0L,
                postureScore = score,
            )
            CampusChallengeSummary(
                challengePoints = rows.sumOf { it.challengePoints },
                postureScore = score,
                validDays = validDays,
                validMinutes = validSeconds / 60L,
                longestGreenStreakSeconds = qualification.longestGreenStreakSeconds,
                qualified = qualification.isEligible(),
            )
        }

    suspend fun leaderboard(
        context: Context,
        entityType: LeaderboardEntityType,
        scopeType: LeaderboardScopeType,
        period: LeaderboardPeriod,
        profile: EducationProfileEntity?,
    ): CachedLeaderboardResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val dao = PostureDatabase.getInstance(appContext).monitoringDao()
        val key = listOf(entityType.name, scopeType.name, period.name, profile?.schoolId, profile?.gradeCode)
            .joinToString("|")
        if (HeadUpAuthStore.isSignedIn(appContext)) {
            runCatching {
                HeadUpApiClient.authenticatedService(appContext).campusLeaderboard(
                    entityType = entityType.name,
                    scopeType = scopeType.name,
                    period = period.name,
                    schoolId = profile?.schoolId,
                    gradeCode = profile?.gradeCode,
                    educationStage = profile?.educationStage,
                )
            }.getOrNull()?.let { response ->
                val now = System.currentTimeMillis()
                dao.upsertLeaderboardCache(
                    LeaderboardCacheEntity(
                        cacheKey = key,
                        entityType = entityType.name,
                        scopeType = scopeType.name,
                        period = period.name,
                        queryJson = gson.toJson(mapOf("schoolId" to profile?.schoolId, "gradeCode" to profile?.gradeCode)),
                        payloadJson = gson.toJson(response),
                        fetchedAtMs = now,
                        expiresAtMs = now + 15 * 60_000L,
                    ),
                )
                return@withContext CachedLeaderboardResult(response, fromCache = false)
            }
        }
        val cache = dao.leaderboardCache(key)
        CachedLeaderboardResult(
            response = cache?.let { runCatching { gson.fromJson(it.payloadJson, CampusLeaderboardResponse::class.java) }.getOrNull() },
            fromCache = cache != null,
            cachedAtMs = cache?.fetchedAtMs,
        )
    }

    fun periodRange(period: LeaderboardPeriod, nowMs: Long = System.currentTimeMillis()): Pair<String, String> {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        return when (period) {
            LeaderboardPeriod.THIS_WEEK -> {
                while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) calendar.add(Calendar.DAY_OF_YEAR, -1)
                DATE_FORMAT.get()!!.format(calendar.time) to DATE_FORMAT.get()!!.format(java.util.Date(nowMs))
            }
            LeaderboardPeriod.LAST_WEEK -> {
                while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                val from = DATE_FORMAT.get()!!.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                from to DATE_FORMAT.get()!!.format(calendar.time)
            }
            LeaderboardPeriod.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                DATE_FORMAT.get()!!.format(calendar.time) to DATE_FORMAT.get()!!.format(java.util.Date(nowMs))
            }
        }
    }

    private val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
