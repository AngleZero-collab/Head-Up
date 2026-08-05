package com.google.mediapipe.examples.poselandmarker

import kotlin.math.hypot
import kotlin.math.roundToInt

class DistanceCalculator(
    private val alpha: Float = DEFAULT_EMA_ALPHA,
) {
    private var smoothedPixelDistance: Float? = null

    fun reset() {
        smoothedPixelDistance = null
    }

    fun estimateDistanceCm(
        leftEye: PixelCoordinate,
        rightEye: PixelCoordinate,
        calibrationConstantK: Float = DEFAULT_CALIBRATION_CONSTANT,
    ): DistanceEstimate? {
        val rawPixelDistance = pixelDistance(leftEye, rightEye)
        if (rawPixelDistance < MIN_VALID_PIXEL_DISTANCE) return null

        val smoothed = smoothedPixelDistance?.let { previous ->
            alpha * rawPixelDistance + (1f - alpha) * previous
        } ?: rawPixelDistance
        smoothedPixelDistance = smoothed

        val distanceCm = (calibrationConstantK / smoothed)
            .roundToInt()
            .coerceIn(MIN_DISTANCE_CM, MAX_DISTANCE_CM)

        return DistanceEstimate(
            rawPixelDistance = rawPixelDistance,
            smoothedPixelDistance = smoothed,
            distanceCm = distanceCm,
        )
    }

    companion object {
        const val DEFAULT_EMA_ALPHA = 0.15f
        const val DEFAULT_CALIBRATION_DISTANCE_CM = 45f
        const val DEFAULT_CALIBRATION_CONSTANT = 3_200f
        private const val MIN_VALID_PIXEL_DISTANCE = 8f
        private const val MIN_DISTANCE_CM = 15
        private const val MAX_DISTANCE_CM = 120

        fun pixelDistance(leftEye: PixelCoordinate, rightEye: PixelCoordinate): Float =
            hypot(
                (leftEye.x - rightEye.x).toDouble(),
                (leftEye.y - rightEye.y).toDouble(),
            ).toFloat()

        fun calibrationConstantFor(knownDistanceCm: Float, smoothedPixelDistance: Float): Float? =
            smoothedPixelDistance
                .takeIf { it >= MIN_VALID_PIXEL_DISTANCE }
                ?.let { knownDistanceCm * it }
    }
}

data class DistanceEstimate(
    val rawPixelDistance: Float,
    val smoothedPixelDistance: Float,
    val distanceCm: Int,
)

data class PixelCoordinate(
    val x: Float,
    val y: Float,
)
