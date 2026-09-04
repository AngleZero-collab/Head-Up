package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

data class MonitoringPerformanceSnapshot(
    val actualInferenceFps: Float,
    val batteryDeltaPercent: Int?,
    val peakThermalStatus: Int?,
    val peakMemoryMb: Int,
)

class MonitoringPerformanceMonitor(private val context: Context) {
    private val startedElapsedMs = SystemClock.elapsedRealtime()
    private val batteryAtStart = batteryPercent()
    private var frames = 0L
    private var peakThermalStatus: Int? = null
    private var peakMemoryMb = 0

    fun onInference() {
        frames++
        val memoryMb = ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)).toInt()
        peakMemoryMb = maxOf(peakMemoryMb, memoryMb)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
            peakThermalStatus = maxOf(peakThermalStatus ?: status, status)
        }
    }

    fun snapshot(): MonitoringPerformanceSnapshot {
        val elapsedSeconds = ((SystemClock.elapsedRealtime() - startedElapsedMs) / 1_000f).coerceAtLeast(0.001f)
        val currentBattery = batteryPercent()
        return MonitoringPerformanceSnapshot(
            actualInferenceFps = frames / elapsedSeconds,
            batteryDeltaPercent = if (batteryAtStart == null || currentBattery == null) null else
                (batteryAtStart - currentBattery).coerceAtLeast(0),
            peakThermalStatus = peakThermalStatus,
            peakMemoryMb = peakMemoryMb,
        )
    }

    private fun batteryPercent(): Int? =
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
}

object MonitoringSessionRecorder {
    private const val TAG = "MonitoringRecorder"
    private val executor = Executors.newSingleThreadExecutor()
    private var active: ActiveSession? = null
    private var pendingReminderId: String? = null
    private var lastWindowState = ScoredPostureState.UNKNOWN

    private data class ActiveSession(
        val context: Context,
        val session: MonitoringSessionEntity,
        val engine: PostureScoringEngine,
        val performance: MonitoringPerformanceMonitor,
        @Volatile var scoringDate: String,
        @Volatile var initialized: Boolean = false,
    )

    @Synchronized
    fun start(context: Context, mode: MonitoringMode, eligibleForRanking: Boolean) {
        if (!mode.recordsPosture) return
        if (active?.session?.mode == mode.name) return
        stop(MonitoringMode.PAUSED)
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val session = MonitoringSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            userId = HeadUpAuthStore.currentUserId(appContext),
            mode = mode.name,
            startedAtMs = now,
            startedElapsedRealtimeMs = elapsed,
            timezone = TimeZone.getDefault().id,
            scoringVersion = ScoringConfig().scoringVersion,
            eligibleForRanking = eligibleForRanking && mode == MonitoringMode.GUARDING,
            deviceSessionId = UUID.randomUUID().toString(),
        )
        val scoringDate = DATE_FORMAT.get()!!.format(Date(now))
        val current = ActiveSession(
            appContext,
            session,
            PostureScoringEngine(),
            MonitoringPerformanceMonitor(appContext),
            scoringDate,
        )
        active = current
        pendingReminderId = null
        lastWindowState = ScoredPostureState.UNKNOWN
        executor.executeSafely {
            val dao = PostureDatabase.getInstance(appContext).monitoringDao()
            if (mode == MonitoringMode.GUARDING) {
                val aggregateId = "${session.userId}|$scoringDate|${MonitoringMode.GUARDING.name}"
                val alreadyCountedMs = (dao.dailyAggregate(aggregateId)?.validSeconds ?: 0L) * 1_000L
                current.engine.reset(alreadyCountedMs)
            }
            dao.upsertSession(session)
            current.initialized = true
        }
    }

    @Synchronized
    fun recordMetrics(metrics: PostureMetrics) {
        record(metrics.toScoredState(), metrics.landmarkConfidence, SystemClock.elapsedRealtime())
    }

    @Synchronized
    fun recordUnknown(elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()) {
        record(ScoredPostureState.UNKNOWN, 0f, elapsedRealtimeMs)
    }

    @Synchronized
    fun onInference() {
        active?.performance?.onInference()
    }

    @Synchronized
    fun reminderTriggered(sound: Boolean, vibration: Boolean, visual: Boolean) {
        val current = active ?: return
        if (MonitoringMode.valueOf(current.session.mode) != MonitoringMode.GUARDING) return
        val now = System.currentTimeMillis()
        val reminder = ReminderEventEntity(
            reminderId = UUID.randomUUID().toString(),
            sessionId = current.session.sessionId,
            userId = current.session.userId,
            triggeredAtMs = now,
            postureState = ScoredPostureState.RED.name,
            soundUsed = sound,
            vibrationUsed = vibration,
            visualUsed = visual,
        )
        pendingReminderId = reminder.reminderId
        executor.executeSafely {
            val dao = PostureDatabase.getInstance(current.context).monitoringDao()
            dao.insertReminder(reminder)
            updateReminderAggregate(dao, current, now, corrected = false, recoverySeconds = 0L)
        }
    }

    @Synchronized
    fun postureCorrected() {
        val current = active ?: return
        val reminderId = pendingReminderId ?: return
        pendingReminderId = null
        val now = System.currentTimeMillis()
        executor.executeSafely {
            val dao = PostureDatabase.getInstance(current.context).monitoringDao()
            val reminder = dao.reminder(reminderId) ?: return@executeSafely
            val recoverySeconds = ((now - reminder.triggeredAtMs).coerceAtLeast(0L) / 1_000L)
            dao.updateReminder(
                reminder.copy(
                    correctedAtMs = now,
                    recoverySeconds = recoverySeconds,
                    successfulCorrection = true,
                    isSynced = false,
                ),
            )
            updateReminderAggregate(dao, current, now, corrected = true, recoverySeconds = recoverySeconds)
        }
    }

    @Synchronized
    fun stop(finalState: MonitoringMode = MonitoringMode.OFF) {
        val current = active ?: return
        active = null
        pendingReminderId = null
        val now = System.currentTimeMillis()
        val performance = current.performance.snapshot()
        executor.executeSafely {
            PostureDatabase.getInstance(current.context).monitoringDao().upsertSession(
                current.session.copy(
                    endedAtMs = now,
                    finalState = finalState.name,
                    actualInferenceFps = performance.actualInferenceFps,
                    batteryDeltaPercent = performance.batteryDeltaPercent,
                    peakThermalStatus = performance.peakThermalStatus,
                    peakMemoryMb = performance.peakMemoryMb,
                ),
            )
        }
    }

    @Synchronized
    fun activeMode(): MonitoringMode? = active?.session?.mode?.let(MonitoringMode::valueOf)

    private fun record(state: ScoredPostureState, confidence: Float, elapsedRealtimeMs: Long) {
        val current = active ?: return
        if (!current.initialized) return
        val wallOffset = current.session.startedAtMs - current.session.startedElapsedRealtimeMs
        val sampleDate = DATE_FORMAT.get()!!.format(Date(wallOffset + elapsedRealtimeMs))
        if (sampleDate != current.scoringDate) {
            current.engine.reset()
            current.scoringDate = sampleDate
            lastWindowState = ScoredPostureState.UNKNOWN
        }
        val windows = current.engine.addSample(state, confidence, elapsedRealtimeMs)
        if (windows.isEmpty()) return
        executor.executeSafely {
            val dao = PostureDatabase.getInstance(current.context).monitoringDao()
            windows.forEach { scored ->
                val startMs = wallOffset + scored.startElapsedRealtimeMs
                val endMs = wallOffset + scored.endElapsedRealtimeMs
                val window = PostureWindowEntity(
                    windowId = "${current.session.sessionId}:${scored.startElapsedRealtimeMs}",
                    sessionId = current.session.sessionId,
                    userId = current.session.userId,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    postureState = scored.postureState.name,
                    averageConfidence = scored.averageConfidence,
                    durationSeconds = scored.durationMs / 1_000L,
                    greenSeconds = scored.greenMs / 1_000L,
                    yellowSeconds = scored.yellowMs / 1_000L,
                    redSeconds = scored.redMs / 1_000L,
                    unknownSeconds = scored.unknownMs / 1_000L,
                    scoreDelta = scored.rawScoreDelta,
                    challengePointsDelta = if (current.session.mode == MonitoringMode.GUARDING.name) {
                        scored.challengePointsDelta
                    } else {
                        0
                    },
                    comboMultiplier = scored.comboMultiplier,
                    scoringVersion = scored.scoringVersion,
                    mode = current.session.mode,
                )
                dao.insertWindow(window)
                updateDailyAggregate(dao, current, window, scored.greenStreakMs / 1_000L)
                lastWindowState = scored.postureState
            }
        }
    }

    private fun updateDailyAggregate(
        dao: MonitoringDao,
        current: ActiveSession,
        window: PostureWindowEntity,
        greenStreakSeconds: Long,
    ) {
        val date = DATE_FORMAT.get()!!.format(Date(window.endTimeMs))
        val id = "${window.userId}|$date|${window.mode}"
        val old = dao.dailyAggregate(id)
        val green = (old?.greenSeconds ?: 0L) + window.greenSeconds
        val yellow = (old?.yellowSeconds ?: 0L) + window.yellowSeconds
        val red = (old?.redSeconds ?: 0L) + window.redSeconds
        val unknown = (old?.unknownSeconds ?: 0L) + window.unknownSeconds
        val valid = green + yellow + red
        val duration = window.durationSeconds
        val isNewGreenStreak = window.postureState == ScoredPostureState.GREEN.name &&
            lastWindowState != ScoredPostureState.GREEN
        val now = System.currentTimeMillis()
        dao.upsertDailyAggregate(
            DailyPostureAggregateEntity(
                aggregateId = id,
                userId = window.userId,
                recordDate = date,
                mode = window.mode,
                greenSeconds = green,
                yellowSeconds = yellow,
                redSeconds = red,
                unknownSeconds = unknown,
                validSeconds = valid,
                rawPoints = (old?.rawPoints ?: 0) + window.scoreDelta,
                challengePoints = (old?.challengePoints ?: 0) + window.challengePointsDelta,
                postureScore = PostureScoreCalculator.calculate(green, yellow, red),
                longestGreenStreakSeconds = maxOf(old?.longestGreenStreakSeconds ?: 0L, greenStreakSeconds),
                greenStreakCount = (old?.greenStreakCount ?: 0) + if (isNewGreenStreak) 1 else 0,
                greenStreakTotalSeconds = (old?.greenStreakTotalSeconds ?: 0L) + window.greenSeconds,
                reminderCount = old?.reminderCount ?: 0,
                successfulCorrections = old?.successfulCorrections ?: 0,
                recoverySecondsTotal = old?.recoverySecondsTotal ?: 0L,
                averageRecoverySeconds = old?.averageRecoverySeconds,
                observationSeconds = (old?.observationSeconds ?: 0L) + if (window.mode == MonitoringMode.OBSERVATION.name) duration else 0L,
                guardingSeconds = (old?.guardingSeconds ?: 0L) + if (window.mode == MonitoringMode.GUARDING.name) duration else 0L,
                scoringVersion = window.scoringVersion,
                updatedAtMs = now,
                idempotencyKey = "$id:${window.scoringVersion}:$now",
                isSynced = false,
            ),
        )
    }

    private fun updateReminderAggregate(
        dao: MonitoringDao,
        current: ActiveSession,
        now: Long,
        corrected: Boolean,
        recoverySeconds: Long,
    ) {
        val date = DATE_FORMAT.get()!!.format(Date(now))
        val id = "${current.session.userId}|$date|${MonitoringMode.GUARDING.name}"
        val old = dao.dailyAggregate(id)
        val corrections = (old?.successfulCorrections ?: 0) + if (corrected) 1 else 0
        val recoveryTotal = (old?.recoverySecondsTotal ?: 0L) + if (corrected) recoverySeconds else 0L
        val reminderCount = (old?.reminderCount ?: 0) + if (corrected) 0 else 1
        dao.upsertDailyAggregate(
            (old ?: emptyAggregate(id, current.session.userId, date, now)).copy(
                reminderCount = reminderCount,
                successfulCorrections = corrections,
                recoverySecondsTotal = recoveryTotal,
                averageRecoverySeconds = if (corrections == 0) null else recoveryTotal.toDouble() / corrections,
                updatedAtMs = now,
                idempotencyKey = "$id:${current.session.scoringVersion}:$now",
                isSynced = false,
            ),
        )
    }

    private fun emptyAggregate(id: String, userId: String, date: String, now: Long) =
        DailyPostureAggregateEntity(
            aggregateId = id,
            userId = userId,
            recordDate = date,
            mode = MonitoringMode.GUARDING.name,
            greenSeconds = 0,
            yellowSeconds = 0,
            redSeconds = 0,
            unknownSeconds = 0,
            validSeconds = 0,
            rawPoints = 0,
            challengePoints = 0,
            postureScore = null,
            longestGreenStreakSeconds = 0,
            greenStreakCount = 0,
            greenStreakTotalSeconds = 0,
            reminderCount = 0,
            successfulCorrections = 0,
            recoverySecondsTotal = 0,
            averageRecoverySeconds = null,
            observationSeconds = 0,
            guardingSeconds = 0,
            scoringVersion = ScoringConfig().scoringVersion,
            updatedAtMs = now,
            idempotencyKey = "$id:${ScoringConfig().scoringVersion}:$now",
        )

    private fun java.util.concurrent.Executor.executeSafely(block: () -> Unit) {
        execute {
            try {
                block()
            } catch (error: Exception) {
                Log.e(TAG, "Unable to persist monitoring aggregate", error)
            }
        }
    }

    private val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
