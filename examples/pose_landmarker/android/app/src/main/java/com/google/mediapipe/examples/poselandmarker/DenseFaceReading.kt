package com.google.mediapipe.examples.poselandmarker

import kotlin.math.*

/** Camera-relative estimates, not anatomical neck angles or a calibrated gaze tracker. */
data class DenseFaceReading(
    val pitch: Float, val yaw: Float, val roll: Float,
    val lowerFaceRatio: Float, val downwardGaze: Boolean,
) {
    companion object {
        fun from(points: List<LandmarkPoint>, aspect: Float): DenseFaceReading? {
            if (points.size < 478 || !aspect.isFinite() || aspect <= 0) return null
            if (points.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) return null
            fun p(i: Int) = points[i].let { it.copy(y = it.y * aspect) }
            val a = p(33)
            val b = p(263)
            val left = if (a.x < b.x) a else b
            val right = if (a.x < b.x) b else a
            val dx = right.x - left.x
            val dy = right.y - left.y
            val eyeWidth = hypot(dx, dy)
            if (eyeWidth < 0.025f) return null
            val ex = (a.x + b.x) / 2
            val ey = (a.y + b.y) / 2
            val ez = (a.z + b.z) / 2
            val chin = p(152)
            val nose = p(1)
            fun vertical(q: LandmarkPoint) = ((q.y - ey) * dx - (q.x - ex) * dy) / eyeWidth
            val height = vertical(chin)
            if (height < eyeWidth * 0.3f) return null
            fun deg(y: Float, x: Float) = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
            val yaw = deg(right.z - left.z, eyeWidth)
            if (abs(yaw) > 55f) return null // Self-occlusion: do not guess pitch/gaze.
            fun eyeDown(top: Int, bottom: Int, iris: Int, c1: Int, c2: Int): Boolean {
                val t = p(top); val d = p(bottom); val i = p(iris)
                val vx = d.x - t.x; val vy = d.y - t.y
                val opening = hypot(vx, vy)
                val width = hypot(p(c1).x - p(c2).x, p(c1).y - p(c2).y)
                if (opening < width * 0.15f || opening < 0.002f) return false // Blink/closed eye.
                val position = ((i.x - t.x) * vx + (i.y - t.y) * vy) / (opening * opening)
                return position in 0.65f..1.05f
            }
            return DenseFaceReading(
                deg(chin.z - ez, height), yaw, deg(dy, dx),
                ((height - vertical(nose)) / height).coerceIn(0f, 1f),
                eyeDown(159, 145, 468, 33, 133) && eyeDown(386, 374, 473, 362, 263),
            )
        }
    }
}
