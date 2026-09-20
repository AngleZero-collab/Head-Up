package com.google.mediapipe.examples.poselandmarker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.cos
import kotlin.math.tan

class PostureAnalyzerTest {
    @Before
    fun resetFilter() {
        PostureAnalyzer.resetSmoothing()
    }

    @Test
    fun analyze_returnsSafeZoneForUprightPose() {
        val metrics = PostureAnalyzer.analyze(samplePose(rawAngle = 5f), calibration = basicCalibration())

        assertEquals(PostureZone.SAFE, metrics?.zone)
        assertEquals(0, metrics?.angleDegrees)
        assertEquals("平衡", metrics?.shoulderBalanceLabel)
    }

    @Test
    fun analyze_exposesFiniteParallaxCosineRatioForSync() {
        val metrics = PostureAnalyzer.analyze(samplePose(rawAngle = 60f))!!

        val expected = cos(Math.toRadians(metrics.rawAngleDegrees.toDouble())).toFloat()
        assertEquals(expected, metrics.parallaxCosineRatio, 0.001f)
        assertTrue(metrics.parallaxCosineRatio in 0f..1f)
        assertTrue(metrics.angularVelocity.isFinite())
    }

    @Test
    fun analyze_treatsTwentyFiveDegreesAsBadPosture() {
        val metrics = PostureAnalyzer.analyze(
            samplePose(rawAngle = 25f),
            calibration = basicCalibration().copy(angleDegrees = 50f),
        )

        assertEquals(25, metrics?.angleDegrees)
        assertEquals(PostureZone.DANGER, metrics?.zone)
        assertEquals("姿勢不良", metrics?.neckCurvatureLabel)
    }

    @Test
    fun emaTrustsOnlyFifteenPercentOfNewFrame() {
        val calibration = basicCalibration().copy(angleDegrees = 60f)
        PostureAnalyzer.analyze(samplePose(rawAngle = 60f), calibration = calibration)
        val metrics = PostureAnalyzer.analyze(samplePose(rawAngle = 0f), calibration = calibration)

        assertEquals(9, metrics?.angleDegrees)
        assertEquals(PostureZone.SAFE, metrics?.zone)
    }

    @Test
    fun rapidFallIsIndependentFromDangerThreshold() {
        val calibration = basicCalibration().copy(angleDegrees = 60f)
        PostureAnalyzer.analyze(samplePose(rawAngle = 40f), calibration = calibration)
        val metrics = PostureAnalyzer.analyze(samplePose(rawAngle = 20f), calibration = calibration)

        assertEquals(23, metrics?.angleDegrees)
        assertEquals(PostureZone.WARNING, metrics?.zone)
        assertTrue(metrics?.isRapidFall == true)
    }

    @Test
    fun slowMovementDoesNotTriggerRapidFall() {
        val calibration = basicCalibration().copy(angleDegrees = 45f)
        PostureAnalyzer.analyze(samplePose(rawAngle = 23f), calibration = calibration)
        val metrics = PostureAnalyzer.analyze(samplePose(rawAngle = 22f), calibration = calibration)

        assertFalse(metrics?.isRapidFall == true)
    }

    @Test
    fun analyze_detectsShoulderImbalance() {
        val metrics = PostureAnalyzer.analyze(
            samplePose(rawAngle = 5f, leftShoulderY = 0.76f, rightShoulderY = 0.88f),
        )

        assertEquals("左右不平衡", metrics?.shoulderBalanceLabel)
    }

    @Test
    fun analyze_usesEyesEarsAndMouthWhenNoseConfidenceIsLow() {
        val points = samplePose(rawAngle = 5f).toMutableList().apply {
            this[0] = this[0].copy(visibility = 0f, presence = 0f)
        }

        assertNotNull(PostureAnalyzer.analyze(points))
    }

    @Test
    fun analyze_returnsNullWhenLandmarksAreMissing() {
        assertNull(PostureAnalyzer.analyze(emptyList()))
    }

    @Test
    fun calibrationMakesUprightPoseThePersonalZero() {
        val calibration = CalibrationProfile(
            angleDegrees = 18f,
            postureRatio = 0.8f,
            shoulderWidth = 0.6f,
        )

        val metrics = PostureAnalyzer.analyze(samplePose(rawAngle = 18f), calibration = calibration)

        assertEquals(0, metrics?.relativeAngleDegrees)
        assertEquals(PostureZone.SAFE, metrics?.zone)
    }

    @Test
    fun calibratedScreenDistanceDetectsMovingTooClose() {
        val calibration = CalibrationProfile(
            angleDegrees = 0f,
            postureRatio = 0.8f,
            shoulderWidth = 0.3f,
            distanceConstantK = 2_400f,
        )

        val metrics = PostureAnalyzer.analyze(
            samplePose(rawAngle = 5f),
            calibration = calibration,
            inputImageWidth = 640,
            inputImageHeight = 480,
        )

        assertTrue(metrics?.isTooClose == true)
        assertEquals(PostureZone.DANGER, metrics?.zone)
        assertEquals(19, metrics?.screenDistanceCm)
    }

    @Test
    fun screenDistanceUsesEyePixelDistance() {
        val metrics = PostureAnalyzer.analyze(
            samplePose(rawAngle = 5f),
            inputImageWidth = 640,
            inputImageHeight = 480,
        )

        assertNotNull(metrics?.eyeDistancePixels)
        assertEquals(31, metrics?.screenDistanceCm)
        assertFalse(metrics?.isTooClose == true)
    }

    @Test
    fun neckCompressionCanRaisePostureRisk() {
        val calibration = CalibrationProfile(
            angleDegrees = 0f,
            postureRatio = 0.82f,
            shoulderWidth = 0.6f,
        )

        val metrics = PostureAnalyzer.analyze(
            samplePose(rawAngle = 5f, faceY = 0.55f),
            calibration = calibration,
        )

        assertTrue((metrics?.neckFlexionDegrees ?: 0) >= 25)
        assertEquals(PostureZone.DANGER, metrics?.zone)
    }

    @Test
    fun uncalibratedBodyProportionCannotCreateFalseNeckDanger() {
        val metrics = PostureAnalyzer.analyze(
            samplePose(rawAngle = 52f, faceY = 0.55f),
            calibration = null,
        )

        assertEquals(0, metrics?.relativeAngleDegrees)
        assertEquals(0, metrics?.neckFlexionDegrees)
        assertEquals(PostureZone.SAFE, metrics?.zone)
    }

    private fun orientationCalibration(): CalibrationProfile {
        val neutral = PostureAnalyzer.analyze(samplePose(0f))!!
        PostureAnalyzer.resetSmoothing()
        return CalibrationProfile(
            angleDegrees = neutral.rawAngleDegrees, postureRatio = neutral.postureRatio,
            shoulderWidth = neutral.shoulderWidth, headLateralDegrees = neutral.rawHeadLateralDegrees,
            headYawDegrees = neutral.rawHeadYawDegrees, headPitchDegrees = neutral.rawHeadPitchDegrees,
        )
    }

    private fun basicCalibration() = CalibrationProfile(
        angleDegrees = 0f,
        postureRatio = 0.8f,
        shoulderWidth = 0.6f,
    )

    @Test
    fun highUprightDepthAngleIsSafeAndCollapseTowardZeroBecomesDanger() {
        val calibration = basicCalibration().copy(angleDegrees = 73f, postureRatio = 0.8f)
        var metrics = PostureAnalyzer.analyze(samplePose(rawAngle = 73f), calibration = calibration)!!
        assertEquals(PostureZone.SAFE, metrics.zone)
        assertEquals(0, metrics.relativeAngleDegrees)

        repeat(30) {
            metrics = PostureAnalyzer.analyze(samplePose(rawAngle = 0f), calibration = calibration)!!
        }
        assertEquals(PostureZone.DANGER, metrics.zone)
        assertTrue(metrics.relativeAngleDegrees >= 25)
    }

    private fun rotateFace(pitch: Float = 0f, yaw: Float = 0f): List<LandmarkPoint> {
        val p = Math.toRadians(pitch.toDouble())
        val y = Math.toRadians(yaw.toDouble())
        return samplePose(0f).mapIndexed { index, point ->
            if (index > 10) point else {
                val dy = point.y - 0.32f
                val dx = point.x - 0.5f
                point.copy(
                    x = 0.5f + dx * kotlin.math.cos(y).toFloat(),
                    y = 0.32f + dy * kotlin.math.cos(p).toFloat(),
                    z = dy * kotlin.math.sin(p).toFloat() + dx * kotlin.math.sin(y).toFloat(),
                )
            }
        }
    }

    @Test
    fun lateralDisplacementInBothDirectionsRaisesRisk() {
        val calibration = orientationCalibration()
        for (shift in listOf(-0.35f, 0.35f)) {
            PostureAnalyzer.resetSmoothing()
            val pose = samplePose(0f).mapIndexed { i, p -> if (i <= 10) p.copy(x = p.x + shift) else p }
            var result: PostureMetrics? = null
            repeat(8) { result = PostureAnalyzer.analyze(pose, calibration = calibration) }
            val confirmed = result!!
            assertTrue(kotlin.math.abs(confirmed.headLateralDegrees!!) >= 25)
            assertEquals(PostureZone.DANGER, confirmed.zone)
        }
    }

    @Test
    fun movingTheWholeBodySidewaysDoesNotCountAsHeadDisplacement() {
        val calibration = orientationCalibration()
        val pose = samplePose(0f).map { it.copy(x = it.x + 0.1f) }
        val result = PostureAnalyzer.analyze(pose, calibration = calibration)!!
        assertEquals(0, result.headLateralDegrees)
        assertEquals(PostureZone.SAFE, result.zone)
    }

    @Test
    fun turningEitherWayRaisesRisk() {
        val calibration = orientationCalibration()
        for (yaw in listOf(-35f, 35f)) {
            PostureAnalyzer.resetSmoothing()
            var result: PostureMetrics? = null
            repeat(8) { result = PostureAnalyzer.analyze(rotateFace(yaw = yaw), calibration = calibration) }
            assertTrue(kotlin.math.abs(result!!.headYawDegrees!!) >= 25)
            assertEquals(PostureZone.DANGER, result!!.zone)
        }
    }

    @Test
    fun excessiveLookingUpAndDownAreDetectedSeparately() {
        val calibration = orientationCalibration()
        for (pitch in listOf(-35f, 35f)) {
            PostureAnalyzer.resetSmoothing()
            var result: PostureMetrics? = null
            repeat(8) { result = PostureAnalyzer.analyze(rotateFace(pitch = pitch), calibration = calibration) }
            assertTrue(kotlin.math.abs(result!!.headPitchDegrees!!) >= 25)
            assertEquals(PostureZone.DANGER, result!!.zone)
            assertEquals(if (pitch < 0) R.string.head_look_up_feedback else R.string.head_look_down_feedback,
                result!!.orientationFeedbackRes())
        }
    }

    @Test
    fun photographedWrongPoseRangeIsConfirmedAsDanger() {
        val calibration = orientationCalibration()
        for (pose in listOf(
            rotateFace(pitch = -28f),
            rotateFace(pitch = 28f),
            rotateFace(yaw = -22f),
            rotateFace(yaw = 22f),
        )) {
            PostureAnalyzer.resetSmoothing()
            var result: PostureMetrics? = null
            repeat(12) { result = PostureAnalyzer.analyze(pose, calibration = calibration) }
            assertEquals(PostureZone.DANGER, result!!.zone)
            assertTrue(result!!.isHeadOrientationConfirmed)
        }
    }

    @Test
    fun smallNaturalHeadMotionRemainsSafe() {
        val calibration = orientationCalibration()
        var result: PostureMetrics? = null
        repeat(12) {
            result = PostureAnalyzer.analyze(rotateFace(pitch = 8f, yaw = 8f), calibration = calibration)
        }
        assertEquals(PostureZone.SAFE, result!!.zone)
        assertFalse(result!!.isHeadOrientationConfirmed)
    }

    @Test
    fun sustainedLowPhoneAngleBecomesDangerAfterCalibration() {
        val calibration = orientationCalibration().copy(deviceTiltDegrees = 90f, deviceWasFlat = false)
        var result: PostureMetrics? = null
        repeat(4) {
            result = PostureAnalyzer.analyze(samplePose(0f), deviceTilt = 30, isFlat = true,
                calibration = calibration)
        }
        assertEquals(PostureZone.DANGER, result!!.zone)
        assertTrue(result!!.isLowPhonePositionConfirmed)
        assertEquals(R.string.phone_too_low_feedback, result!!.orientationFeedbackRes())
    }

    @Test
    fun flatPhoneWhileRecliningIsAlsoPoorPosture() {
        val calibration = orientationCalibration().copy(deviceTiltDegrees = 90f, deviceWasFlat = false)
        var result: PostureMetrics? = null
        repeat(8) {
            result = PostureAnalyzer.analyze(
                samplePose(0f), deviceTilt = -90, isFlat = true,
                isLikelyLyingDown = true, calibration = calibration,
            )
        }
        assertEquals(PostureZone.DANGER, result!!.zone)
        assertTrue(result!!.isLowPhonePositionConfirmed)
        assertTrue(result!!.isLikelyLyingDown)
    }

    @Test
    fun smallDeviceTiltChangeRemainsSafe() {
        val calibration = orientationCalibration().copy(deviceTiltDegrees = 90f, deviceWasFlat = false)
        var result: PostureMetrics? = null
        repeat(8) {
            result = PostureAnalyzer.analyze(samplePose(0f), deviceTilt = 85, isFlat = false,
                calibration = calibration)
        }
        assertEquals(PostureZone.SAFE, result!!.zone)
        assertFalse(result!!.isLowPhonePositionConfirmed)
    }

    @Test
    fun deviceTiltComparisonHandlesAngleWraparound() {
        val calibration = orientationCalibration().copy(deviceTiltDegrees = 179f, deviceWasFlat = false)
        val result = PostureAnalyzer.analyze(samplePose(0f), deviceTilt = -179, calibration = calibration)!!
        assertEquals(2, result.deviceTiltDeltaDegrees)
        assertEquals(PostureZone.SAFE, result.zone)
    }

    @Test
    fun moderateTurnAndHeadLiftReachReminderEligibleDanger() {
        val calibration = orientationCalibration()
        for (pose in listOf(
            rotateFace(pitch = -15f),
            rotateFace(pitch = 15f),
            rotateFace(yaw = -15f),
            rotateFace(yaw = 15f),
        )) {
            PostureAnalyzer.resetSmoothing()
            var result: PostureMetrics? = null
            repeat(8) { result = PostureAnalyzer.analyze(pose, calibration = calibration) }
            assertEquals(PostureZone.DANGER, result!!.zone)
            assertTrue(result!!.isHeadOrientationConfirmed)
        }
    }

    @Test
    fun returningFromLookingDownToNeutralRestoresSafeState() {
        val calibration = orientationCalibration()
        PostureAnalyzer.analyze(rotateFace(pitch = 35f), calibration = calibration)
        var result: PostureMetrics? = null
        repeat(60) { result = PostureAnalyzer.analyze(samplePose(0f), calibration = calibration) }
        assertEquals(PostureZone.SAFE, result!!.zone)
        assertEquals(0, result!!.headPitchDegrees)
    }

    @Test
    fun briefTurnIsSmoothedAndMissingEyesDoNotReuseItsAngle() {
        val calibration = orientationCalibration()
        PostureAnalyzer.analyze(samplePose(0f), calibration = calibration)
        val brief = PostureAnalyzer.analyze(rotateFace(yaw = 35f), calibration = calibration)!!
        assertEquals(PostureZone.SAFE, brief.zone)
        val missingEye = rotateFace(yaw = 35f).toMutableList()
        missingEye[2] = missingEye[2].copy(visibility = 0.1f)
        val missing = PostureAnalyzer.analyze(missingEye, calibration = calibration)!!
        assertNull(missing.headYawDegrees)
        assertNull(missing.headPitchDegrees)
    }

    @Test
    fun oldCalibrationRequiresNewPitchBaseline() {
        val old = CalibrationProfile(0f, 0.82f, 0.6f)
        assertNull(PostureAnalyzer.analyze(rotateFace(pitch = -35f), calibration = old)!!.headPitchDegrees)
    }

    @Test
    fun orientationIsIndependentOfImageAspectRatio() {
        val calibration = orientationCalibration()
        val pose = rotateFace(pitch = -35f)
        val square = PostureAnalyzer.analyze(pose, calibration = calibration)!!
        PostureAnalyzer.resetSmoothing()
        val wide = PostureAnalyzer.analyze(pose.map { it.copy(y = it.y / 0.75f) },
            calibration = calibration, inputImageWidth = 640, inputImageHeight = 480)!!
        assertEquals(square.headPitchDegrees, wide.headPitchDegrees)
        assertEquals(square.headLateralDegrees, wide.headLateralDegrees)
    }

    @Test
    fun denseFaceCalibrationKeepsNeutralSafeAndDetectsRoll() {
        val calibration = orientationCalibration().copy(
            headPitchDegrees = 12f, headYawDegrees = -4f, headRollDegrees = 3f,
            lowerFaceRatio = 0.6f,
        )
        val neutral = DenseFaceReading(12f, -4f, 3f, 0.6f, false)
        repeat(8) {
            assertEquals(PostureZone.SAFE, PostureAnalyzer.analyze(
                samplePose(0f), calibration = calibration, denseFace = neutral)!!.zone)
        }
        var result: PostureMetrics? = null
        repeat(30) {
            result = PostureAnalyzer.analyze(samplePose(0f), calibration = calibration,
                denseFace = neutral.copy(roll = 23f))
        }
        assertEquals(PostureZone.DANGER, result!!.zone)
        assertEquals(18, result!!.headRollDegrees)
    }

    @Test
    fun gazeAloneNeverRaisesPostureRisk() {
        val calibration = orientationCalibration().copy(
            headPitchDegrees = 0f, headYawDegrees = 0f, headRollDegrees = 0f,
            lowerFaceRatio = 0.6f,
        )
        repeat(8) {
            assertEquals(PostureZone.SAFE, PostureAnalyzer.analyze(samplePose(0f),
                calibration = calibration,
                denseFace = DenseFaceReading(0f, 0f, 0f, 0.5f, true))!!.zone)
        }
    }

    @Test
    fun flatPhoneDoesNotRequireCalibrationToRaiseRisk() {
        var result: PostureMetrics? = null
        repeat(8) {
            result = PostureAnalyzer.analyze(samplePose(0f), isFlat = true)
        }
        assertEquals(PostureZone.DANGER, result!!.zone)
    }

    private fun samplePose(
        rawAngle: Float,
        faceY: Float = 0.32f,
        leftShoulderY: Float = 0.80f,
        rightShoulderY: Float = 0.80f,
    ): List<LandmarkPoint> {
        val shoulderCenterY = (leftShoulderY + rightShoulderY) / 2f
        val faceZ = -tan(Math.toRadians(rawAngle.toDouble())).toFloat() * (shoulderCenterY - faceY)
        fun facePoint(x: Float, y: Float) = LandmarkPoint(x, y, z = faceZ)

        return MutableList(33) {
            LandmarkPoint(0.5f, 0.5f, visibility = 0f, presence = 0f)
        }.apply {
            this[0] = facePoint(0.50f, faceY)
            this[1] = facePoint(0.42f, faceY - 0.03f)
            this[2] = facePoint(0.40f, faceY - 0.03f)
            this[3] = facePoint(0.38f, faceY - 0.03f)
            this[4] = facePoint(0.58f, faceY - 0.03f)
            this[5] = facePoint(0.60f, faceY - 0.03f)
            this[6] = facePoint(0.62f, faceY - 0.03f)
            this[7] = facePoint(0.34f, faceY - 0.02f)
            this[8] = facePoint(0.66f, faceY - 0.02f)
            this[9] = facePoint(0.45f, faceY + 0.06f)
            this[10] = facePoint(0.55f, faceY + 0.06f)
            this[11] = LandmarkPoint(0.2f, leftShoulderY, z = 0f)
            this[12] = LandmarkPoint(0.8f, rightShoulderY, z = 0f)
            this[23] = LandmarkPoint(0.30f, 1.20f, z = 0f)
            this[24] = LandmarkPoint(0.70f, 1.20f, z = 0f)
        }
    }
}
