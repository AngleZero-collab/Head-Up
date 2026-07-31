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
            val dao = PostureDatabase.getInstance(applicationContext).postureRecordDao()
            val pending = dao.unsyncedRecords(SYNC_BATCH_LIMIT)
            if (pending.isEmpty()) return@withContext Result.success()

            val token = HeadUpAuthStore.accessToken(applicationContext)
                ?: return@withContext Result.success()
            val payload = pending.toDailySyncPayload()
            if (payload.isEmpty()) return@withContext Result.success()

            val response = HeadUpApiClient.service.syncRecords("Bearer $token", payload)
            if (response.isSuccessful) {
                dao.markSynced(pending.map { it.id }, System.currentTimeMillis())
                Result.success()
            } else if (response.code() in 500..599 || response.code() == 429) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (_: Exception) {
            Log.w(TAG, "Posture sync failed; WorkManager will retry later.")
            Result.retry()
        }
    }

    private fun List<PostureRecordEntity>.toDailySyncPayload(): List<SyncPostureRecordRequest> =
        groupBy { it.recordDateIso() }
            .map { (recordDate, records) ->
                val dangerEvents = records.countDangerEvents()
                val rapidFalls = records.count { it.isRapidFall }
                val denominator = maxOf(dangerEvents, rapidFalls, 1)
                SyncPostureRecordRequest(
                    userId = records.first().userId,
                    dailySlouchCount = dangerEvents,
                    aiInterceptRate = (rapidFalls.toFloat() / denominator.toFloat()).coerceIn(0f, 1f),
                    recordDate = recordDate,
                )
            }

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

    companion object {
        private const val TAG = "PostureSyncWorker"
        private const val SYNC_BATCH_LIMIT = 1_000
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
