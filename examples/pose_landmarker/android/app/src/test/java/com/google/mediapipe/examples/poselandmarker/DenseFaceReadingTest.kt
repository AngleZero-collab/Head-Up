package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DenseFaceReadingTest {
    private fun face(): List<LandmarkPoint> = MutableList(478) { LandmarkPoint(0.5f, 0.4f) }.apply {
        this[33] = LandmarkPoint(0.35f, 0.3f)
        this[263] = LandmarkPoint(0.65f, 0.3f)
        this[152] = LandmarkPoint(0.5f, 0.65f)
        this[1] = LandmarkPoint(0.5f, 0.44f)
    }

    @Test fun neutralAndRotatedFaceHaveIndependentRoll() {
        val neutral = DenseFaceReading.from(face(), 1f)!!
        assertEquals(0f, neutral.pitch, 0.01f)
        assertEquals(0f, neutral.yaw, 0.01f)
        val angle = Math.toRadians(25.0)
        val rotated = face().map {
            val x = it.x - 0.5f; val y = it.y - 0.4f
            it.copy(x = (0.5 + x*cos(angle)-y*sin(angle)).toFloat(),
                y = (0.4 + x*sin(angle)+y*cos(angle)).toFloat())
        }
        val result = DenseFaceReading.from(rotated, 1f)!!
        assertEquals(25f, result.roll, 0.01f)
        assertEquals(neutral.pitch, result.pitch, 0.01f)
        assertEquals(neutral.lowerFaceRatio, result.lowerFaceRatio, 0.01f)
    }

    @Test fun badGeometryAndInvalidCoordinatesProduceNoReading() {
        assertNull(DenseFaceReading.from(emptyList(), 1f))
        assertNull(DenseFaceReading.from(face(), Float.NaN))
        assertNull(DenseFaceReading.from(face().map { it.copy(x = Float.NaN) }, 1f))
        assertNull(DenseFaceReading.from(List(478) { LandmarkPoint(0.5f, 0.5f) }, 1f))
    }

    @Test fun closedEyesDoNotCountAsDownwardGaze() {
        assertFalse(DenseFaceReading.from(face(), 1f)!!.downwardGaze)
    }

    @Test fun imageAspectRatioDoesNotChangeAngles() {
        val a = DenseFaceReading.from(face(), 1f)!!
        val b = DenseFaceReading.from(face().map { it.copy(y = it.y / 0.75f) }, 0.75f)!!
        assertEquals(a.pitch, b.pitch, 0.001f)
        assertEquals(a.lowerFaceRatio, b.lowerFaceRatio, 0.001f)
    }
}
