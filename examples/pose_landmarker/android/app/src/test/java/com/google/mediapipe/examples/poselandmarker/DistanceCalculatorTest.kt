package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceCalculatorTest {
    @Test
    fun pixelDistanceUsesEuclideanDistanceBetweenEyes() {
        val pixelDistance = DistanceCalculator.pixelDistance(
            PixelCoordinate(10f, 20f),
            PixelCoordinate(13f, 24f),
        )

        assertEquals(5f, pixelDistance, 0.001f)
    }

    @Test
    fun distanceMapsSmoothedPixelDistanceThroughCalibrationConstant() {
        val calculator = DistanceCalculator()
        val estimate = calculator.estimateDistanceCm(
            PixelCoordinate(0f, 0f),
            PixelCoordinate(64f, 0f),
            calibrationConstantK = 3_200f,
        )

        assertEquals(50, estimate?.distanceCm)
    }

    @Test
    fun emaTrustsFifteenPercentOfNewEyeDistance() {
        val calculator = DistanceCalculator()
        calculator.estimateDistanceCm(
            PixelCoordinate(0f, 0f),
            PixelCoordinate(100f, 0f),
        )
        val estimate = calculator.estimateDistanceCm(
            PixelCoordinate(0f, 0f),
            PixelCoordinate(200f, 0f),
        )

        assertTrue((estimate?.smoothedPixelDistance ?: 0f) in 114.9f..115.1f)
    }
}
