package com.google.mediapipe.examples.poselandmarker

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Pose landmark estimates, not anatomical neck angles. Coordinates use image-width units.
 * https://github.com/google-ai-edge/mediapipe/blob/master/docs/solutions/pose.md
 */
internal class HeadOrientationAnalyzer {
    data class Reading(val lateral: Float?, val yaw: Float?, val pitch: Float?)

    private var previous = Reading(null, null, null)

    fun reset() { previous = Reading(null, null, null) }

    fun analyze(points: List<LandmarkPoint>, width: Int?, height: Int?): Reading {
        val aspect = if (width != null && height != null && width > 0 && height > 0) {
            height.toFloat() / width
        } else 1f
        fun point(index: Int) = points.getOrNull(index)?.takeIf {
            it.visibility >= 0.5f && it.presence >= 0.5f &&
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
        }?.let { it.copy(y = it.y * aspect) }
        fun center(a: LandmarkPoint, b: LandmarkPoint) = LandmarkPoint(
            (a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2,
        )
        fun degrees(y: Float, x: Float) = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
        val leftShoulder = point(11)
        val rightShoulder = point(12)
        val leftEye = point(2)
        val rightEye = point(5)
        var lateral: Float? = null
        var yaw: Float? = null
        var pitch: Float? = null
        if (leftShoulder != null && rightShoulder != null && leftEye != null && rightEye != null) {
            val shoulders = listOf(leftShoulder, rightShoulder).sortedBy { it.x }
            val eyes = listOf(leftEye, rightEye).sortedBy { it.x }
            val shoulderDx = shoulders[1].x - shoulders[0].x
            val shoulderDy = shoulders[1].y - shoulders[0].y
            val shoulderWidth = hypot(shoulderDx, shoulderDy)
            val eyeWidth = hypot(eyes[1].x - eyes[0].x, eyes[1].y - eyes[0].y)
            if (shoulderWidth > 0.02f && eyeWidth > 0.01f) {
                val shoulderCenter = center(leftShoulder, rightShoulder)
                val eyeCenter = center(leftEye, rightEye)
                // Project onto the shoulder axis so a tilted camera does not look like lateral movement.
                val dx = eyeCenter.x - shoulderCenter.x
                val dy = eyeCenter.y - shoulderCenter.y
                val side = (dx * shoulderDx + dy * shoulderDy) / shoulderWidth
                val up = (dx * shoulderDy - dy * shoulderDx) / shoulderWidth
                // Express displacement as a fraction of shoulder width. Using the eye-to-shoulder
                // height here made the result change when the user looked up/down or sat taller.
                if (up > shoulderWidth * 0.15f) lateral = degrees(side, shoulderWidth)
                yaw = degrees(eyes[1].z - eyes[0].z, eyeWidth) -
                    degrees(shoulders[1].z - shoulders[0].z, shoulderWidth)
                val mouthLeft = point(9)
                val mouthRight = point(10)
                // Near-profile faces do not provide a reliable two-eye pitch estimate.
                if (mouthLeft != null && mouthRight != null && abs(yaw) < 45f) {
                    val mouth = center(mouthLeft, mouthRight)
                    val faceHeight = hypot(mouth.x - eyeCenter.x, mouth.y - eyeCenter.y)
                    if (faceHeight > 0.01f && mouth.y > eyeCenter.y) {
                        // Depth alone is noisy in BlazePose's small face landmark set. Blend it with
                        // the nose-to-ear vertical ratio, which supplies a stable sign for up/down.
                        val depthPitch = degrees(mouth.z - eyeCenter.z, faceHeight)
                        val nose = point(0)
                        val leftEar = point(7)
                        val rightEar = point(8)
                        val shapePitch = if (nose != null && leftEar != null && rightEar != null) {
                            val earCenter = center(leftEar, rightEar)
                            degrees(nose.y - earCenter.y, eyeWidth).coerceIn(-60f, 60f)
                        } else null
                        pitch = shapePitch?.let { depthPitch * 0.85f + it * 0.15f } ?: depthPitch
                    }
                }
            }
        }
        fun smooth(value: Float?, old: Float?) = value?.let { current ->
            old?.let { prior ->
                val alpha = if (abs(current - prior) >= 10f) 0.45f else 0.25f
                prior + alpha * (current - prior)
            } ?: current
        }
        // Missing points clear the axis filter instead of retaining a stale risk estimate.
        return Reading(smooth(lateral, previous.lateral), smooth(yaw, previous.yaw), smooth(pitch, previous.pitch))
            .also { previous = it }
    }
}
