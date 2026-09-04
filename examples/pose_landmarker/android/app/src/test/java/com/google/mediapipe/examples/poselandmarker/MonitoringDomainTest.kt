package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringDomainTest {
    @Test
    fun `reminders only arm in guarding mode after three seconds`() {
        assertFalse(ReminderPolicy.shouldArm(MonitoringMode.OBSERVATION, 10_000L))
        assertFalse(ReminderPolicy.shouldArm(MonitoringMode.GUARDING, 2_999L))
        assertTrue(ReminderPolicy.shouldArm(MonitoringMode.GUARDING, 3_000L))
    }

    @Test
    fun `reminder cooldown uses elapsed time`() {
        assertFalse(ReminderPolicy.canEmit(MonitoringMode.OBSERVATION, 70_000L, null))
        assertFalse(ReminderPolicy.canEmit(MonitoringMode.GUARDING, 70_000L, 20_000L))
        assertTrue(ReminderPolicy.canEmit(MonitoringMode.GUARDING, 80_000L, 20_000L))
    }

    @Test
    fun scoresGreenYellowRedAndUnknownWindows() {
        assertEquals(10, oneWindow(ScoredPostureState.GREEN).rawScoreDelta)
        assertEquals(-5, oneWindow(ScoredPostureState.YELLOW).rawScoreDelta)
        assertEquals(-15, oneWindow(ScoredPostureState.RED).rawScoreDelta)
        assertEquals(0, oneWindow(ScoredPostureState.UNKNOWN, confidence = 0f).rawScoreDelta)
    }

    @Test
    fun greenComboUsesConfiguredThresholdsAndCapsAtTwoTimes() {
        val engine = PostureScoringEngine(ScoringConfig(maximumSampleGapMs = 10_000L))
        val windows = mutableListOf<ScoredPostureWindow>()
        engine.addSample(ScoredPostureState.GREEN, 1f, 0L)
        for (second in 10..300 step 10) {
            windows += engine.addSample(ScoredPostureState.GREEN, 1f, second * 1_000L)
        }
        assertEquals(1.0, windows[4].comboMultiplier, 0.001)
        assertEquals(1.2, windows[5].comboMultiplier, 0.001)
        assertEquals(1.5, windows[17].comboMultiplier, 0.001)
        assertEquals(2.0, windows[29].comboMultiplier, 0.001)
        assertEquals(20, windows.last().rawScoreDelta)
    }

    @Test
    fun yellowAndRedEndGreenCombo() {
        val engine = PostureScoringEngine(ScoringConfig(maximumSampleGapMs = 120_000L))
        engine.addSample(ScoredPostureState.GREEN, 1f, 0L)
        engine.addSample(ScoredPostureState.YELLOW, 1f, 70_000L)
        val yellow = engine.addSample(ScoredPostureState.GREEN, 1f, 80_000L).single()
        val afterYellow = engine.addSample(ScoredPostureState.RED, 1f, 90_000L).single()
        val afterRed = engine.addSample(ScoredPostureState.GREEN, 1f, 100_000L).single()
        assertEquals(-5, yellow.rawScoreDelta)
        assertEquals(10, afterYellow.rawScoreDelta)
        assertEquals(-15, afterRed.rawScoreDelta)
        assertEquals(0L, afterRed.greenStreakMs)
    }

    @Test
    fun briefUnknownPausesButLongUnknownEndsCombo() {
        val engine = PostureScoringEngine(ScoringConfig(maximumSampleGapMs = 120_000L))
        engine.addSample(ScoredPostureState.GREEN, 1f, 0L)
        engine.addSample(ScoredPostureState.UNKNOWN, 1f, 50_000L)
        engine.addSample(ScoredPostureState.GREEN, 1f, 54_000L)
        val shortGapWindow = engine.addSample(ScoredPostureState.GREEN, 1f, 60_000L).single()
        assertEquals(56_000L, shortGapWindow.greenStreakMs)

        engine.addSample(ScoredPostureState.UNKNOWN, 1f, 65_000L)
        engine.addSample(ScoredPostureState.GREEN, 1f, 71_000L)
        val longGapWindow = engine.addSample(ScoredPostureState.GREEN, 1f, 80_000L).last()
        assertTrue(longGapWindow.greenStreakMs < 20_000L)
    }

    @Test
    fun lowConfidenceIsUnknownAndCarriesScoringVersion() {
        val engine = PostureScoringEngine(ScoringConfig(scoringVersion = 7))
        engine.addSample(ScoredPostureState.GREEN, 0.1f, 0L)
        val window = engine.addSample(ScoredPostureState.GREEN, 0.1f, 10_000L).single()
        assertEquals(ScoredPostureState.UNKNOWN, window.postureState)
        assertEquals(7, window.scoringVersion)
    }

    @Test
    fun challengePointsStopAtDailyValidTimeCapButRawScoreRemains() {
        val engine = PostureScoringEngine(
            ScoringConfig(dailyChallengeValidSecondsLimit = 10L, maximumSampleGapMs = 10_000L),
        )
        engine.addSample(ScoredPostureState.GREEN, 1f, 0L)
        val first = engine.addSample(ScoredPostureState.GREEN, 1f, 10_000L).single()
        val second = engine.addSample(ScoredPostureState.GREEN, 1f, 20_000L).single()
        assertEquals(10, first.challengePointsDelta)
        assertEquals(0, second.challengePointsDelta)
        assertEquals(10, second.rawScoreDelta)
    }

    @Test
    fun restoredDailyUsageCannotBypassChallengeCapAfterRestart() {
        val engine = PostureScoringEngine(
            ScoringConfig(dailyChallengeValidSecondsLimit = 10L, maximumSampleGapMs = 10_000L),
        )
        engine.reset(dayChallengeValidMs = 5_000L)
        engine.addSample(ScoredPostureState.GREEN, 1f, 0L)
        val partial = engine.addSample(ScoredPostureState.GREEN, 1f, 10_000L).single()
        val capped = engine.addSample(ScoredPostureState.GREEN, 1f, 20_000L).single()

        assertEquals(5, partial.challengePointsDelta)
        assertEquals(0, capped.challengePointsDelta)
        assertEquals(10, capped.rawScoreDelta)
    }

    @Test
    fun postureScoreAndZeroDenominatorAreCorrect() {
        assertEquals(62.5, PostureScoreCalculator.calculate(50, 25, 25)!!, 0.001)
        assertNull(PostureScoreCalculator.calculate(0, 0, 0))
    }

    @Test
    fun guardComparisonRequiresThirtyMinutesForBothModes() {
        val observation = aggregate(MonitoringMode.OBSERVATION, 900, 450, 450)
        val guarding = aggregate(MonitoringMode.GUARDING, 1_350, 300, 150)
        val result = GuardEffectivenessCalculator.compare(observation, guarding)
        assertTrue(result.hasEnoughData)
        assertEquals(25.0, result.greenImprovementPercentagePoints!!, 0.001)
        assertEquals(50.0, result.badPostureReductionPercent!!, 0.001)

        val insufficient = GuardEffectivenessCalculator.compare(observation.copy(greenSeconds = 100), guarding)
        assertFalse(insufficient.hasEnoughData)
        assertNull(insufficient.greenImprovementPercentagePoints)
    }

    @Test
    fun zeroBadPostureBaselineDoesNotDivideByZero() {
        val observation = aggregate(MonitoringMode.OBSERVATION, 1_800, 0, 0)
        val guarding = aggregate(MonitoringMode.GUARDING, 1_800, 0, 0)
        val result = GuardEffectivenessCalculator.compare(observation, guarding)
        assertTrue(result.hasEnoughData)
        assertNull(result.badPostureReductionPercent)
        assertEquals("zero_observation_baseline", result.insufficientReason)
    }

    @Test
    fun leaderboardEligibilityAndSchoolMedianAreFair() {
        assertTrue(LeaderboardQualification(3, 1_800, 300, 88.0).isEligible())
        assertFalse(LeaderboardQualification(2, 9_000, 500, 99.0).isEligible())
        assertNull(SchoolScoreCalculator.medianEligibleScore(listOf(90.0, 20.0, 80.0, 70.0)))
        assertEquals(80.0, SchoolScoreCalculator.medianEligibleScore(listOf(90.0, 20.0, 80.0, 70.0, 100.0))!!, 0.001)
    }

    private fun oneWindow(state: ScoredPostureState, confidence: Float = 1f): ScoredPostureWindow {
        val engine = PostureScoringEngine(ScoringConfig(maximumSampleGapMs = 10_000L))
        engine.addSample(state, confidence, 0L)
        return engine.addSample(state, confidence, 10_000L).single()
    }

    private fun aggregate(mode: MonitoringMode, green: Long, yellow: Long, red: Long) =
        ModePostureAggregate(mode, green, yellow, red, 0, longestGreenStreakSeconds = green)
}
