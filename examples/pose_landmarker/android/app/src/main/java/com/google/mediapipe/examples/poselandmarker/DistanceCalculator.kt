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
            val isFastApproach = rawPixelDistance > previous * FAST_APPROACH_RATIO &&
                rawPixelDistance - previous >= FAST_APPROACH_MIN_DELTA_PX
            val effectiveAlpha = if (isFastApproach) {
                FAST_APPROACH_ALPHA
            } else {
                alpha
            }
            val candidate = effectiveAlpha * rawPixelDistance + (1f - effectiveAlpha) * previous
            if (isFastApproach) {
                candidate.coerceAtLeast(rawPixelDistance * FAST_APPROACH_MIN_RAW_RATIO)
            } else {
                candidate
            }
        } ?: rawPixelDistance
        smoothedPixelDistance = smoothed

        val smoothedDistanceCm = (calibrationConstantK / smoothed)
            .roundToInt()
            .coerceIn(MIN_DISTANCE_CM, MAX_DISTANCE_CM)
        val rawDistanceCm = (calibrationConstantK / rawPixelDistance)
            .roundToInt()
            .coerceIn(MIN_DISTANCE_CM, MAX_DISTANCE_CM)

        val displayDistanceCm = if (rawDistanceCm <= IMMEDIATE_WARNING_DISTANCE_CM) {
            rawDistanceCm
        } else {
            smoothedDistanceCm
        }

        return DistanceEstimate(
            rawPixelDistance = rawPixelDistance,
            smoothedPixelDistance = smoothed,
            distanceCm = displayDistanceCm,
            rawDistanceCm = rawDistanceCm,
        )
    }

    companion object {
        const val DEFAULT_EMA_ALPHA = 0.15f
        const val DEFAULT_CALIBRATION_DISTANCE_CM = 45f
        const val DEFAULT_CALIBRATION_CONSTANT = 4_000f
        private const val FAST_APPROACH_ALPHA = 0.82f
        private const val FAST_APPROACH_RATIO = 1.12f
        private const val FAST_APPROACH_MIN_DELTA_PX = 18f
        private const val FAST_APPROACH_MIN_RAW_RATIO = 0.90f
        private const val IMMEDIATE_WARNING_DISTANCE_CM = 30
        private const val MIN_VALID_PIXEL_DISTANCE = 8f
        private const val MIN_DISTANCE_CM = 10
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
    val rawDistanceCm: Int,
)

data class PixelCoordinate(
    val x: Float,
    val y: Float,
)
