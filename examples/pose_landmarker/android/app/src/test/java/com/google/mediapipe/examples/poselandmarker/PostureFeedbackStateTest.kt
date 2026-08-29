package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertEquals
import org.junit.Test

class PostureFeedbackStateTest {
    @Test
    fun activeAlertKeepsEveryFeedbackSurfaceInDangerDuringRecoveryGrace() {
        val state = HeadUpUiState(
            metrics = metrics(PostureZone.SAFE),
            isPostureAlertActive = true,
        )

        assertEquals(PostureZone.DANGER, state.feedbackZone)
    }

    @Test
    fun dangerFrameIsOnlyCautionBeforeSharedAlertActivates() {
        val state = HeadUpUiState(
            metrics = metrics(PostureZone.DANGER),
            isPostureAlertActive = false,
        )

        assertEquals(PostureZone.WARNING, state.feedbackZone)
    }

    @Test
    fun safeFeedbackReturnsOnlyAfterSharedAlertClears() {
        val state = HeadUpUiState(
            metrics = metrics(PostureZone.SAFE),
            isPostureAlertActive = false,
        )

        assertEquals(PostureZone.SAFE, state.feedbackZone)
    }

    private fun metrics(zone: PostureZone) = PostureMetrics(
        angleDegrees = 0,
        zone = zone,
        postureRatio = 0f,
        headTiltLabel = "",
        neckCurvatureLabel = "",
        shoulderBalanceDegrees = 0,
        shoulderBalanceLabel = "",
    )
}
