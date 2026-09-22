package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class PostureSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = PostureDatabase.getInstance(applicationContext)
            val dao = database.postureRecordDao()
            val monitoringDao = database.monitoringDao()
            val pending = dao.unsyncedRecords(SYNC_BATCH_LIMIT)
            val pendingAggregates = monitoringDao.unsyncedAggregates(AGGREGATE_BATCH_LIMIT)
            if (pending.isEmpty() && pendingAggregates.isEmpty()) return@withContext Result.success()

            if (!ensureSyncToken()) {
                return@withContext Result.retry()
            }
            val service = HeadUpApiClient.authenticatedService(applicationContext)
            if (pendingAggregates.isNotEmpty()) {
                val aggregateResponse = service.syncPostureAggregates(
                    PostureAggregateBatchRequest(pendingAggregates.map { it.toUpload() }),
                )
                if (aggregateResponse.isSuccessful) {
                    monitoringDao.markAggregatesSynced(
                        pendingAggregates.map { it.aggregateId },
                        System.currentTimeMillis(),
                    )
                } else if (aggregateResponse.code() in 500..599 || aggregateResponse.code() == 429) {
                    return@withContext Result.retry()
                } else {
                    return@withContext Result.failure()
                }
            }

            val payload = pending.toDailySyncPayload()
            if (payload.isNotEmpty()) {
                val response = service.syncDailyReports(payload)
                if (response.isSuccessful) {
                    dao.markSynced(pending.map { it.id }, System.currentTimeMillis())
                } else if (response.code() in 500..599 || response.code() == 429) {
                    return@withContext Result.retry()
                } else {
                    return@withContext Result.failure()
                }
            }
            Result.success()
        } catch (_: Exception) {
            Log.w(TAG, "Posture sync failed; WorkManager will retry later.")
            Result.retry()
        }
    }

    private suspend fun ensureSyncToken(): Boolean {
        if (!HeadUpAuthStore.accessToken(applicationContext).isNullOrBlank()) return true

        return try {
            val token = HeadUpApiClient.service.guest(
                GuestLoginRequest(HeadUpAuthStore.deviceUserId(applicationContext)),
            )
            HeadUpAuthStore.saveSession(
                applicationContext,
                token.accessToken,
                token.userId,
                token.subscriptionTier,
                token.role,
            )
            true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to create backend guest session for posture sync.", error)
            false
        }
    }

    private fun List<PostureRecordEntity>.toDailySyncPayload(): List<DailyReportSyncRequest> =
        groupBy { it.recordDateIso() }
            .map { (recordDate, records) ->
                val dangerEvents = records.countDangerEvents()
                val rapidFalls = records.count { it.isRapidFall }
                val denominator = maxOf(dangerEvents, rapidFalls, 1)
                DailyReportSyncRequest(
                    recordDate = recordDate,
                    slouchCount = dangerEvents,
                    aiInterceptRate = (rapidFalls.toFloat() / denominator.toFloat()).coerceIn(0f, 1f),
                    petExp = records.petExp(),
                )
            }

    private fun DailyPostureAggregateEntity.toUpload() = PostureAggregateUpload(
        aggregateId = aggregateId,
        recordDate = recordDate,
        mode = mode,
        greenSeconds = greenSeconds,
        yellowSeconds = yellowSeconds,
        redSeconds = redSeconds,
        unknownSeconds = unknownSeconds,
        rawPoints = rawPoints,
        challengePoints = challengePoints,
        longestGreenStreakSeconds = longestGreenStreakSeconds,
        greenStreakCount = greenStreakCount,
        greenStreakTotalSeconds = greenStreakTotalSeconds,
        reminderCount = reminderCount,
        successfulCorrections = successfulCorrections,
        recoverySecondsTotal = recoverySecondsTotal,
        scoringVersion = scoringVersion,
        idempotencyKey = idempotencyKey,
    )

    private fun List<PostureRecordEntity>.countDangerEvents(): Int {
        var previousDanger = false
        var events = 0
        forEach { record ->
            val danger = record.zone == PostureZone.DANGER.name
            if (danger && !previousDanger) events++
            previousDanger = danger
        }
        return events
    }

    private fun PostureRecordEntity.recordDateIso(): String =
        DATE_FORMAT.get()!!.format(Date(timestampMs))

    private fun List<PostureRecordEntity>.petExp(): Int =
        filter { it.zone == PostureZone.SAFE.name }
            .sumOf { it.durationMs }
            .div(60_000L)
            .toInt()
            .coerceAtLeast(0)

    companion object {
        private const val TAG = "PostureSyncWorker"
        private const val SYNC_BATCH_LIMIT = 1_000
        private const val AGGREGATE_BATCH_LIMIT = 100
        private val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
        }
    }
}

object PostureSyncScheduler {
    private const val PERIODIC_WORK_NAME = "headup-posture-sync"
    private const val ONE_TIME_WORK_NAME = "headup-posture-sync-now"

    private val syncConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<PostureSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(syncConstraints)
            .addTag(PERIODIC_WORK_NAME)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueOneTime(context: Context) {
        val request = OneTimeWorkRequestBuilder<PostureSyncWorker>()
            .setConstraints(syncConstraints)
            .addTag(ONE_TIME_WORK_NAME)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
