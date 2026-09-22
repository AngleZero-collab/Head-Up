package com.google.mediapipe.examples.poselandmarker

import kotlin.math.roundToInt

enum class MonitoringMode {
    OFF,
    OBSERVATION,
    GUARDING,
    PAUSED,
    CAMERA_UNAVAILABLE,
    PERMISSION_REQUIRED,
    ERROR,
    ;

    val recordsPosture: Boolean
        get() = this == OBSERVATION || this == GUARDING

    val allowsPostureReminders: Boolean
        get() = this == GUARDING
}

enum class ScoredPostureState {
    GREEN,
    YELLOW,
    RED,
    UNKNOWN,
}

enum class LeaderboardEntityType {
    USER,
    SCHOOL,
}

enum class LeaderboardScopeType {
    SCHOOL,
    GRADE_IN_SCHOOL,
    GRADE_IN_COUNTRY,
    EDUCATION_STAGE,
    COUNTRY,
    GLOBAL,
}

enum class LeaderboardPeriod {
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
}

data class ScoringConfig(
    val scoringVersion: Int = 1,
    val windowDurationMs: Long = 10_000L,
    val unknownComboGraceMs: Long = 5_000L,
    val maximumSampleGapMs: Long = 2_000L,
    val minimumConfidence: Float = 0.20f,
    val greenPoints: Int = 10,
    val yellowPoints: Int = -5,
    val redPoints: Int = -15,
    val dailyChallengeValidSecondsLimit: Long = 3_600L,
    val leaderboardMinimumValidDays: Int = 3,
    val leaderboardMinimumValidSeconds: Long = 1_800L,
    val schoolMinimumParticipants: Int = 5,
    val comparisonMinimumValidSecondsPerMode: Long = 1_800L,
) {
    init {
        require(windowDurationMs > 0L)
        require(unknownComboGraceMs >= 0L)
        require(maximumSampleGapMs > 0L)
        require(greenPoints > 0)
        require(redPoints < yellowPoints && yellowPoints < 0)
        require(dailyChallengeValidSecondsLimit > 0L)
    }

    fun multiplierFor(greenStreakMs: Long): Double = when {
        greenStreakMs >= 300_000L -> 2.0
        greenStreakMs >= 180_000L -> 1.5
        greenStreakMs >= 60_000L -> 1.2
        else -> 1.0
    }
}

interface MonotonicClock {
    fun elapsedRealtimeMs(): Long
}

object ReminderPolicy {
    fun shouldArm(mode: MonitoringMode, continuousDangerMs: Long, delayMs: Long = 3_000L): Boolean =
        mode == MonitoringMode.GUARDING && continuousDangerMs >= delayMs

    fun canEmit(
        mode: MonitoringMode,
        nowElapsedMs: Long,
        lastReminderElapsedMs: Long?,
        cooldownMs: Long = 60_000L,
    ): Boolean = mode == MonitoringMode.GUARDING &&
        (lastReminderElapsedMs == null || nowElapsedMs - lastReminderElapsedMs >= cooldownMs)
}

object AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMs(): Long = android.os.SystemClock.elapsedRealtime()
}

data class ScoredPostureWindow(
    val startElapsedRealtimeMs: Long,
    val endElapsedRealtimeMs: Long,
    val postureState: ScoredPostureState,
    val averageConfidence: Float,
    val greenMs: Long,
    val yellowMs: Long,
    val redMs: Long,
    val unknownMs: Long,
    val rawScoreDelta: Int,
    val challengePointsDelta: Int,
    val comboMultiplier: Double,
    val greenStreakMs: Long,
    val scoringVersion: Int,
) {
    val durationMs: Long
        get() = greenMs + yellowMs + redMs + unknownMs

    val validMs: Long
        get() = greenMs + yellowMs + redMs
}

class PostureScoringEngine(
    val config: ScoringConfig = ScoringConfig(),
    private val clock: MonotonicClock = AndroidMonotonicClock,
) {
    private var windowStartMs: Long? = null
    private var lastSampleTimeMs: Long? = null
    private var lastState = ScoredPostureState.UNKNOWN
    private var lastConfidence = 0f
    private val durations = LongArray(ScoredPostureState.entries.size)
    private var confidenceDurationProduct = 0.0
    private var confidenceDurationMs = 0L
    private var greenStreakMs = 0L
    private var unknownGapMs = 0L
    private var challengeValidMsToday = 0L

    @Synchronized
    fun addSample(
        state: ScoredPostureState,
        confidence: Float,
        elapsedRealtimeMs: Long = clock.elapsedRealtimeMs(),
    ): List<ScoredPostureWindow> {
        val normalizedState = if (confidence < config.minimumConfidence) ScoredPostureState.UNKNOWN else state
        val previousTime = lastSampleTimeMs
        if (previousTime == null || elapsedRealtimeMs < previousTime) {
            resetTimeline(elapsedRealtimeMs, normalizedState, confidence)
            return emptyList()
        }

        val completed = mutableListOf<ScoredPostureWindow>()
        val gapMs = elapsedRealtimeMs - previousTime
        val attributedMs = minOf(gapMs, config.maximumSampleGapMs)
        consume(previousTime, previousTime + attributedMs, lastState, lastConfidence, completed)
        if (gapMs > attributedMs) {
            consume(
                previousTime + attributedMs,
                elapsedRealtimeMs,
                ScoredPostureState.UNKNOWN,
                0f,
                completed,
            )
        }
        lastSampleTimeMs = elapsedRealtimeMs
        lastState = normalizedState
        lastConfidence = confidence.coerceIn(0f, 1f)
        return completed
    }

    @Synchronized
    fun reset(dayChallengeValidMs: Long = 0L) {
        windowStartMs = null
        lastSampleTimeMs = null
        lastState = ScoredPostureState.UNKNOWN
        lastConfidence = 0f
        durations.fill(0L)
        confidenceDurationProduct = 0.0
        confidenceDurationMs = 0L
        greenStreakMs = 0L
        unknownGapMs = 0L
        challengeValidMsToday = dayChallengeValidMs.coerceAtLeast(0L)
    }

    private fun resetTimeline(timeMs: Long, state: ScoredPostureState, confidence: Float) {
        windowStartMs = timeMs
        lastSampleTimeMs = timeMs
        lastState = state
        lastConfidence = confidence.coerceIn(0f, 1f)
        durations.fill(0L)
        confidenceDurationProduct = 0.0
        confidenceDurationMs = 0L
        unknownGapMs = 0L
    }

    private fun consume(
        fromMs: Long,
        toMs: Long,
        state: ScoredPostureState,
        confidence: Float,
        completed: MutableList<ScoredPostureWindow>,
    ) {
        if (toMs <= fromMs) return
        if (windowStartMs == null) windowStartMs = fromMs
        var cursor = fromMs
        while (cursor < toMs) {
            val start = windowStartMs ?: cursor
            val boundary = start + config.windowDurationMs
            val segmentEnd = minOf(toMs, boundary)
            val durationMs = segmentEnd - cursor
            durations[state.ordinal] += durationMs
            updateGreenStreak(state, durationMs)
            if (state != ScoredPostureState.UNKNOWN) {
                confidenceDurationProduct += confidence.coerceIn(0f, 1f) * durationMs.toDouble()
                confidenceDurationMs += durationMs
            }
            cursor = segmentEnd
            if (cursor >= boundary) {
                completed += finishWindow(start, boundary)
                windowStartMs = boundary
            }
        }
    }

    private fun updateGreenStreak(state: ScoredPostureState, durationMs: Long) {
        when (state) {
            ScoredPostureState.GREEN -> {
                if (unknownGapMs > config.unknownComboGraceMs) greenStreakMs = 0L
                unknownGapMs = 0L
                greenStreakMs += durationMs
            }
            ScoredPostureState.YELLOW, ScoredPostureState.RED -> {
                greenStreakMs = 0L
                unknownGapMs = 0L
            }
            ScoredPostureState.UNKNOWN -> {
                unknownGapMs += durationMs
                if (unknownGapMs > config.unknownComboGraceMs) greenStreakMs = 0L
            }
        }
    }

    private fun finishWindow(startMs: Long, endMs: Long): ScoredPostureWindow {
        val validDurations = ScoredPostureState.entries
            .filter { it != ScoredPostureState.UNKNOWN }
            .associateWith { durations[it.ordinal] }
        val state = validDurations.maxByOrNull { it.value }
            ?.takeIf { it.value > 0L }
            ?.key
            ?: ScoredPostureState.UNKNOWN
        val multiplier = if (state == ScoredPostureState.GREEN) config.multiplierFor(greenStreakMs) else 1.0
        val basePoints = when (state) {
            ScoredPostureState.GREEN -> config.greenPoints
            ScoredPostureState.YELLOW -> config.yellowPoints
            ScoredPostureState.RED -> config.redPoints
            ScoredPostureState.UNKNOWN -> 0
        }
        val rawDelta = (basePoints * multiplier).roundToInt()
        val validMs = validDurations.values.sum()
        val capMs = config.dailyChallengeValidSecondsLimit * 1_000L
        val eligibleMs = minOf(validMs, (capMs - challengeValidMsToday).coerceAtLeast(0L))
        val challengeDelta = if (validMs == 0L) 0 else (rawDelta * eligibleMs.toDouble() / validMs).roundToInt()
        challengeValidMsToday += eligibleMs
        val window = ScoredPostureWindow(
            startElapsedRealtimeMs = startMs,
            endElapsedRealtimeMs = endMs,
            postureState = state,
            averageConfidence = if (confidenceDurationMs == 0L) 0f else
                (confidenceDurationProduct / confidenceDurationMs.toDouble()).toFloat(),
            greenMs = durations[ScoredPostureState.GREEN.ordinal],
            yellowMs = durations[ScoredPostureState.YELLOW.ordinal],
            redMs = durations[ScoredPostureState.RED.ordinal],
            unknownMs = durations[ScoredPostureState.UNKNOWN.ordinal],
            rawScoreDelta = rawDelta,
            challengePointsDelta = challengeDelta,
            comboMultiplier = multiplier,
            greenStreakMs = greenStreakMs,
            scoringVersion = config.scoringVersion,
        )
        durations.fill(0L)
        confidenceDurationProduct = 0.0
        confidenceDurationMs = 0L
        return window
    }
}

data class ModePostureAggregate(
    val mode: MonitoringMode,
    val greenSeconds: Long,
    val yellowSeconds: Long,
    val redSeconds: Long,
    val unknownSeconds: Long,
    val longestGreenStreakSeconds: Long,
    val greenStreakCount: Int = 0,
    val greenStreakTotalSeconds: Long = 0L,
    val reminderCount: Int = 0,
    val successfulCorrections: Int = 0,
    val recoverySecondsTotal: Long = 0L,
) {
    val validSeconds: Long
        get() = greenSeconds + yellowSeconds + redSeconds

    val greenRate: Double?
        get() = validSeconds.takeIf { it > 0L }?.let { greenSeconds.toDouble() / it }

    val yellowRate: Double?
        get() = validSeconds.takeIf { it > 0L }?.let { yellowSeconds.toDouble() / it }

    val redRate: Double?
        get() = validSeconds.takeIf { it > 0L }?.let { redSeconds.toDouble() / it }

    val badRate: Double?
        get() = validSeconds.takeIf { it > 0L }?.let { (yellowSeconds + redSeconds).toDouble() / it }

    val postureScore: Double?
        get() = PostureScoreCalculator.calculate(greenSeconds, yellowSeconds, redSeconds)
}

object PostureScoreCalculator {
    fun calculate(greenSeconds: Long, yellowSeconds: Long, redSeconds: Long): Double? {
        val valid = greenSeconds + yellowSeconds + redSeconds
        if (valid <= 0L) return null
        return (100.0 * (greenSeconds + 0.5 * yellowSeconds) / valid).coerceIn(0.0, 100.0)
    }
}

data class GuardEffectivenessComparison(
    val observation: ModePostureAggregate,
    val guarding: ModePostureAggregate,
    val hasEnoughData: Boolean,
    val greenImprovementPercentagePoints: Double?,
    val badPostureReductionPercent: Double?,
    val insufficientReason: String? = null,
)

object GuardEffectivenessCalculator {
    fun compare(
        observation: ModePostureAggregate,
        guarding: ModePostureAggregate,
        config: ScoringConfig = ScoringConfig(),
    ): GuardEffectivenessComparison {
        val enough = observation.validSeconds >= config.comparisonMinimumValidSecondsPerMode &&
            guarding.validSeconds >= config.comparisonMinimumValidSecondsPerMode
        if (!enough) {
            return GuardEffectivenessComparison(
                observation,
                guarding,
                hasEnoughData = false,
                greenImprovementPercentagePoints = null,
                badPostureReductionPercent = null,
                insufficientReason = "minimum_valid_time",
            )
        }
        val observationGreen = observation.greenRate ?: 0.0
        val guardingGreen = guarding.greenRate ?: 0.0
        val observationBad = observation.badRate ?: 0.0
        val guardingBad = guarding.badRate ?: 0.0
        return GuardEffectivenessComparison(
            observation,
            guarding,
            hasEnoughData = true,
            greenImprovementPercentagePoints = (guardingGreen - observationGreen) * 100.0,
            badPostureReductionPercent = if (observationBad == 0.0) null else
                ((observationBad - guardingBad) / observationBad) * 100.0,
            insufficientReason = if (observationBad == 0.0) "zero_observation_baseline" else null,
        )
    }
}

data class LeaderboardQualification(
    val validDays: Int,
    val validSeconds: Long,
    val longestGreenStreakSeconds: Long,
    val postureScore: Double?,
) {
    fun isEligible(config: ScoringConfig = ScoringConfig()): Boolean =
        validDays >= config.leaderboardMinimumValidDays &&
            validSeconds >= config.leaderboardMinimumValidSeconds &&
            postureScore != null
}

object SchoolScoreCalculator {
    fun medianEligibleScore(scores: List<Double>, config: ScoringConfig = ScoringConfig()): Double? {
        if (scores.size < config.schoolMinimumParticipants) return null
        val sorted = scores.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}

fun PostureMetrics.toScoredState(minimumConfidence: Float = ScoringConfig().minimumConfidence): ScoredPostureState =
    if (landmarkConfidence < minimumConfidence) ScoredPostureState.UNKNOWN else when (zone) {
        PostureZone.SAFE -> ScoredPostureState.GREEN
        PostureZone.WARNING -> ScoredPostureState.YELLOW
        PostureZone.DANGER -> ScoredPostureState.RED
    }
