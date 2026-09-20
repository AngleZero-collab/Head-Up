package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object PostureAnalyzer {
    private const val NOSE = 0
    private const val LEFT_EYE_INNER = 1
    private const val LEFT_EYE = 2
    private const val LEFT_EYE_OUTER = 3
    private const val RIGHT_EYE_INNER = 4
    private const val RIGHT_EYE = 5
    private const val RIGHT_EYE_OUTER = 6
    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val MOUTH_LEFT = 9
    private const val MOUTH_RIGHT = 10
    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24

    private const val SAFE_LIMIT_DEGREES = 15
    private const val BAD_POSTURE_LIMIT_DEGREES = 25
    private const val RAPID_FALL_ARM_DEGREES = 22
    private const val RAPID_FALL_VELOCITY = 0.5f
    private const val MIN_TRACKING_CONFIDENCE = 0.20f
    private const val TOO_CLOSE_WARNING_CM = 30
    private const val TOO_CLOSE_DANGER_CM = 20
    private const val SHOULDER_WARNING_DEGREES = 8
    private const val SHOULDER_DANGER_DEGREES = 14
    private const val NECK_RATIO_TO_DEGREES = 110f
    private const val EMA_ALPHA = 0.15f
    private const val ORIENTATION_DEAD_ZONE_DEGREES = 2f
    private const val ORIENTATION_WARNING_DEGREES = 7
    private const val ORIENTATION_DANGER_DEGREES = 10
    private const val ORIENTATION_CONFIRM_FRAMES = 3
    private const val DEVICE_TILT_WARNING_DEGREES = 8
    private const val DEVICE_TILT_DANGER_DEGREES = 14
    private const val DEVICE_TILT_CONFIRM_FRAMES = 3
    private const val SHOULDER_DISTANCE_OVERRIDE_MARGIN_CM = 3
    private const val STABILITY_WINDOW_MS = 3_000L
    private const val STABLE_ANGULAR_VELOCITY_DEGREES_PER_SECOND = 3f

    private var smoothedAngle: Float? = null
    private var previousSmoothedAngle: Float? = null
    private var previousAnalysisTimestampMs: Long? = null
    private var stableSinceMs: Long? = null
    private val distanceCalculator = DistanceCalculator()
    private val headOrientationAnalyzer = HeadOrientationAnalyzer()
    private var orientationEvidenceFrames = 0
    private var deviceTiltEvidenceFrames = 0
    private var previousDenseFace: DenseFaceReading? = null

    @Synchronized
    fun resetSmoothing() {
        smoothedAngle = null
        previousSmoothedAngle = null
        previousAnalysisTimestampMs = null
        stableSinceMs = null
        distanceCalculator.reset()
        headOrientationAnalyzer.reset()
        orientationEvidenceFrames = 0
        deviceTiltEvidenceFrames = 0
        previousDenseFace = null
    }

    fun defaultMetrics(): PostureMetrics = PostureMetrics(
        angleDegrees = 0,
        zone = PostureZone.SAFE,
        postureRatio = 0f,
        headTiltLabel = "正常",
        neckCurvatureLabel = "正常",
        shoulderBalanceDegrees = 0,
        shoulderBalanceLabel = "平衡",
    )

    fun analyzeMediaPipe(
        landmarks: List<NormalizedLandmark>,
        deviceTilt: Int = 0,
        isFlat: Boolean = false,
        isLikelyLyingDown: Boolean = false,
        calibration: CalibrationProfile? = null,
        inputImageWidth: Int? = null,
        inputImageHeight: Int? = null,
        denseFace: DenseFaceReading? = null,
    ): PostureMetrics? = analyze(
        points = landmarks.map { landmark ->
            LandmarkPoint(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(1f),
                presence = landmark.presence().orElse(1f),
            )
        },
        deviceTilt = deviceTilt,
        isFlat = isFlat,
        isLikelyLyingDown = isLikelyLyingDown,
        calibration = calibration,
        inputImageWidth = inputImageWidth,
        inputImageHeight = inputImageHeight,
        denseFace = denseFace,
    )

    @Synchronized
    fun analyze(
        points: List<LandmarkPoint>,
        deviceTilt: Int = 0,
        isFlat: Boolean = false,
        isLikelyLyingDown: Boolean = false,
        calibration: CalibrationProfile? = null,
        inputImageWidth: Int? = null,
        inputImageHeight: Int? = null,
        denseFace: DenseFaceReading? = null,
    ): PostureMetrics? {
        val body = BodyLandmarks.from(points) ?: return null
        val shoulderWidth = distance2d(body.leftShoulder, body.rightShoulder)
        if (shoulderWidth <= 0.001f) return null

        val shoulderCenter = midpoint(body.leftShoulder, body.rightShoulder)
        val faceCenter = body.faceCenter
        val verticalDistance = (shoulderCenter.y - faceCenter.y).coerceAtLeast(0.001f)
        val postureRatio = verticalDistance / shoulderWidth
        val depthDistance = shoulderCenter.z - faceCenter.z
        // 以臉部到肩部的 3D 向量計算相對垂直軸 cosine ratio，值域自然落在 0..1。
        val parallaxCosineRatio = verticalDistance / hypot(verticalDistance, depthDistance)

        // Keep the original 15-point 3D vector core: face center to shoulder center in Y/Z space.
        val rawAngle = Math.toDegrees(
            atan2(
                depthDistance.toDouble(),
                verticalDistance.toDouble(),
            ),
        ).toFloat().coerceIn(0f, 90f)

        val currentSmoothed = smoothedAngle?.let { previous ->
            EMA_ALPHA * rawAngle + (1f - EMA_ALPHA) * previous
        } ?: rawAngle
        // An upright face has a larger face-to-shoulder depth angle on the front camera;
        // forward collapse makes that angle smaller. Velocity follows the same risk direction.
        val analyzedAtMs = System.currentTimeMillis()
        val previousAngle = previousSmoothedAngle
        val angleVelocity = previousAngle?.let { it - currentSmoothed } ?: 0f
        // API 儲存實際的角速度（度/秒）；快速倒下既有判斷仍沿用每幀差值，避免改變警示行為。
        val elapsedSeconds = previousAnalysisTimestampMs
            ?.let { (analyzedAtMs - it).coerceAtLeast(1L) / 1_000f }
        val angularVelocity = if (previousAngle != null && elapsedSeconds != null) {
            (previousAngle - currentSmoothed) / elapsedSeconds
        } else {
            0f
        }
        previousSmoothedAngle = currentSmoothed
        previousAnalysisTimestampMs = analyzedAtMs
        smoothedAngle = currentSmoothed

        // Calibration captures the correct upright angle. A drop from that baseline is
        // deterioration. Without calibration, the raw depth angle is not a risk angle.
        val relativeHeadAngle = calibration?.let {
            (it.angleDegrees - currentSmoothed).coerceAtLeast(0f)
        } ?: 0f
        // Body proportions and camera framing vary too much for a universal neck ratio.
        // Only compare compression after a personal correct-posture baseline exists.
        val neckFlexion = calibration?.postureRatio
            ?.takeIf { it > 0.05f }
            ?.let { ratioBaseline ->
                val neckCompression = ((ratioBaseline - postureRatio) / ratioBaseline)
                    .coerceAtLeast(0f)
                (neckCompression * NECK_RATIO_TO_DEGREES).coerceIn(0f, 60f)
            } ?: 0f
        val compositeAngle = max(relativeHeadAngle, neckFlexion)
        val sparse = headOrientationAnalyzer.analyze(points, inputImageWidth, inputImageHeight)
        val filteredFace = denseFace?.let { current ->
            fun smooth(value: Float, old: Float?): Float {
                if (old == null) return value
                val delta = ((value - old + 540f) % 360f) - 180f
                return old + 0.35f * delta
            }
            current.copy(
                pitch = smooth(current.pitch, previousDenseFace?.pitch),
                yaw = smooth(current.yaw, previousDenseFace?.yaw),
                roll = smooth(current.roll, previousDenseFace?.roll),
            )
        }
        previousDenseFace = filteredFace
        val orientation = if (filteredFace == null) sparse else sparse.copy(
            yaw = filteredFace.yaw, pitch = filteredFace.pitch,
        )
        fun relativeOrientation(value: Float?, baseline: Float?): Int? {
            if (value == null || baseline == null) return null
            val difference = value - baseline
            return when {
                abs(difference) <= ORIENTATION_DEAD_ZONE_DEGREES -> 0
                difference > 0f -> (difference - ORIENTATION_DEAD_ZONE_DEGREES).roundToInt()
                else -> (difference + ORIENTATION_DEAD_ZONE_DEGREES).roundToInt()
            }
        }
        val lateral = relativeOrientation(orientation.lateral, calibration?.headLateralDegrees)
        val yaw = relativeOrientation(orientation.yaw, calibration?.headYawDegrees)
        // Pitch needs a personal baseline: the eye/mouth depth offset differs between faces.
        val pitch = relativeOrientation(orientation.pitch, calibration?.headPitchDegrees)
        val roll = relativeOrientation(filteredFace?.roll, calibration?.headRollDegrees)
        val corroboratedDown = denseFace != null && denseFace.downwardGaze &&
            (pitch ?: 0) >= 7 && calibration?.lowerFaceRatio != null &&
            denseFace.lowerFaceRatio < calibration.lowerFaceRatio - 0.06f
        val orientationRisk = maxOf(
            abs(roll ?: 0),
            if (corroboratedDown) 10 else 0,
            abs(lateral ?: 0),
            abs(yaw ?: 0),
            abs(pitch ?: 0),
        )
        orientationEvidenceFrames = if (orientationRisk >= ORIENTATION_WARNING_DEGREES) {
            (orientationEvidenceFrames + 1).coerceAtMost(ORIENTATION_CONFIRM_FRAMES)
        } else {
            (orientationEvidenceFrames - 2).coerceAtLeast(0)
        }
        // New orientation signals are additive. They may change the original posture zone only
        // after several consecutive frames agree, so one noisy face landmark cannot take over.
        val confirmedOrientationRisk = if (orientationEvidenceFrames >= ORIENTATION_CONFIRM_FRAMES) {
            orientationRisk
        } else 0

        fun circularAngleDistance(a: Float, b: Float): Float {
            val difference = ((a - b + 180f) % 360f + 360f) % 360f - 180f
            return abs(difference)
        }
        val deviceTiltDelta = calibration?.deviceTiltDegrees?.let { baseline ->
            circularAngleDistance(deviceTilt.toFloat(), baseline).roundToInt()
        }
        // A nearly horizontal phone encourages looking down or reclining use. Treat either
        // screen direction as risk; the gyro remains available as diagnostic context.
        val lowPhoneEvidence = isFlat ||
            deviceTiltDelta?.let { it >= DEVICE_TILT_WARNING_DEGREES } == true
        deviceTiltEvidenceFrames = if (lowPhoneEvidence) {
            (deviceTiltEvidenceFrames + 1).coerceAtMost(DEVICE_TILT_CONFIRM_FRAMES)
        } else {
            (deviceTiltEvidenceFrames - 2).coerceAtLeast(0)
        }
        val isLowPhoneConfirmed = deviceTiltEvidenceFrames >= DEVICE_TILT_CONFIRM_FRAMES
        val confirmedDeviceRisk = if (!isLowPhoneConfirmed) 0 else when {
            isFlat -> DEVICE_TILT_DANGER_DEGREES
            else -> deviceTiltDelta ?: 0
        }

        val shoulderBalanceAngle = pairAngleDegrees(body.leftShoulder, body.rightShoulder)
        val distanceEstimate = estimateScreenDistance(body, calibration, inputImageWidth, inputImageHeight)
        val screenDistanceCm = distanceEstimate?.distanceCm
        val isTooClose = screenDistanceCm?.let { it < TOO_CLOSE_WARNING_CM } ?: false
        val angle = compositeAngle.roundToInt()
        val isRapidFall = angle >= RAPID_FALL_ARM_DEGREES && angleVelocity > RAPID_FALL_VELOCITY
        val hasStableSignal = abs(angularVelocity) <= STABLE_ANGULAR_VELOCITY_DEGREES_PER_SECOND &&
            body.confidence >= MIN_TRACKING_CONFIDENCE && !isRapidFall
        if (hasStableSignal) {
            if (stableSinceMs == null) stableSinceMs = analyzedAtMs
        } else {
            stableSinceMs = null
        }
        // 必須連續穩定三秒才標記成功，單一平穩影格不會被誤認為通過防手震視窗。
        val isStable = hasStableSignal &&
            (stableSinceMs?.let { analyzedAtMs - it >= STABILITY_WINDOW_MS } == true)

        val zone = when {
            confirmedDeviceRisk >= DEVICE_TILT_DANGER_DEGREES -> PostureZone.DANGER
            confirmedOrientationRisk >= ORIENTATION_DANGER_DEGREES -> PostureZone.DANGER
            angle >= BAD_POSTURE_LIMIT_DEGREES -> PostureZone.DANGER
            screenDistanceCm != null && screenDistanceCm < TOO_CLOSE_DANGER_CM -> PostureZone.DANGER
            shoulderBalanceAngle >= SHOULDER_DANGER_DEGREES -> PostureZone.DANGER
            confirmedDeviceRisk >= DEVICE_TILT_WARNING_DEGREES -> PostureZone.WARNING
            confirmedOrientationRisk >= ORIENTATION_WARNING_DEGREES -> PostureZone.WARNING
            angle >= SAFE_LIMIT_DEGREES -> PostureZone.WARNING
            isTooClose -> PostureZone.WARNING
            shoulderBalanceAngle >= SHOULDER_WARNING_DEGREES -> PostureZone.WARNING
            else -> PostureZone.SAFE
        }

        return PostureMetrics(
            angleDegrees = angle,
            rawAngleDegrees = currentSmoothed,
            parallaxCosineRatio = parallaxCosineRatio,
            angularVelocity = angularVelocity,
            isStable = isStable,
            relativeAngleDegrees = relativeHeadAngle.roundToInt(),
            neckFlexionDegrees = neckFlexion.roundToInt(),
            zone = zone,
            postureRatio = postureRatio,
            headTiltLabel = when {
                relativeHeadAngle >= BAD_POSTURE_LIMIT_DEGREES -> "前傾過大"
                relativeHeadAngle >= SAFE_LIMIT_DEGREES -> "輕微前傾"
                else -> "正常"
            },
            neckCurvatureLabel = when (zone) {
                PostureZone.SAFE -> "正常"
                PostureZone.WARNING -> "警戒"
                PostureZone.DANGER -> "姿勢不良"
            },
            shoulderBalanceDegrees = shoulderBalanceAngle.roundToInt(),
            shoulderBalanceLabel = if (shoulderBalanceAngle < SHOULDER_WARNING_DEGREES) "平衡" else "左右不平衡",
            screenDistanceCm = screenDistanceCm,
            isTooClose = isTooClose,
            eyeDistancePixels = distanceEstimate?.rawPixelDistance,
            smoothedEyeDistancePixels = distanceEstimate?.smoothedPixelDistance,
            landmarkConfidence = body.confidence,
            shoulderWidth = shoulderWidth,
            deviceTiltDegrees = deviceTilt,
            isDeviceFlat = isFlat,
            isRapidFall = isRapidFall,
            timestampMs = analyzedAtMs,
            rawHeadLateralDegrees = orientation.lateral,
            rawHeadYawDegrees = orientation.yaw,
            rawHeadPitchDegrees = orientation.pitch,
            headLateralDegrees = lateral,
            headYawDegrees = yaw,
            headPitchDegrees = pitch,
            rawHeadRollDegrees = filteredFace?.roll,
            headRollDegrees = roll,
            lowerFaceRatio = denseFace?.lowerFaceRatio,
            isHeadOrientationConfirmed = confirmedOrientationRisk >= ORIENTATION_WARNING_DEGREES,
            deviceTiltDeltaDegrees = deviceTiltDelta,
            isLowPhonePositionConfirmed = isLowPhoneConfirmed,
            isLikelyLyingDown = isLikelyLyingDown,
        )
    }

    fun zoneForAngle(angleDegrees: Int): PostureZone = when {
        angleDegrees < SAFE_LIMIT_DEGREES -> PostureZone.SAFE
        angleDegrees < BAD_POSTURE_LIMIT_DEGREES -> PostureZone.WARNING
        else -> PostureZone.DANGER
    }

    private data class BodyLandmarks(
        val faceCenter: LandmarkPoint,
        val leftShoulder: LandmarkPoint,
        val rightShoulder: LandmarkPoint,
        val hipCenter: LandmarkPoint?,
        val confidence: Float,
        val leftEyeCenter: LandmarkPoint?,
        val rightEyeCenter: LandmarkPoint?,
    ) {
        companion object {
            fun from(points: List<LandmarkPoint>): BodyLandmarks? {
                val leftShoulder = points.tracked(LEFT_SHOULDER) ?: return null
                val rightShoulder = points.tracked(RIGHT_SHOULDER) ?: return null
                val nose = points.tracked(NOSE)
                val leftEye = weightedCenter(
                    listOfNotNull(
                        points.tracked(LEFT_EYE_INNER)?.let { it to 1f },
                        points.tracked(LEFT_EYE)?.let { it to 1.25f },
                        points.tracked(LEFT_EYE_OUTER)?.let { it to 1f },
                    ),
                )
                val rightEye = weightedCenter(
                    listOfNotNull(
                        points.tracked(RIGHT_EYE_INNER)?.let { it to 1f },
                        points.tracked(RIGHT_EYE)?.let { it to 1.25f },
                        points.tracked(RIGHT_EYE_OUTER)?.let { it to 1f },
                    ),
                )
                val eyes = weightedCenter(
                    listOfNotNull(
                        leftEye?.let { it to 1f },
                        rightEye?.let { it to 1f },
                    ),
                )
                val ears = weightedCenter(
                    listOfNotNull(
                        points.tracked(LEFT_EAR)?.let { it to 1f },
                        points.tracked(RIGHT_EAR)?.let { it to 1f },
                    ),
                )
                val mouth = weightedCenter(
                    listOfNotNull(
                        points.tracked(MOUTH_LEFT)?.let { it to 1f },
                        points.tracked(MOUTH_RIGHT)?.let { it to 1f },
                    ),
                )
                val faceParts = listOfNotNull(
                    nose?.let { it to 3f },
                    eyes?.let { it to 2.5f },
                    ears?.let { it to 1.5f },
                    mouth?.let { it to 1.5f },
                )
                val faceCenter = weightedCenter(faceParts) ?: return null
                val hips = weightedCenter(
                    listOfNotNull(
                        points.tracked(LEFT_HIP)?.let { it to 1f },
                        points.tracked(RIGHT_HIP)?.let { it to 1f },
                    ),
                )
                val confidencePoints = faceParts.map { it.first } + leftShoulder + rightShoulder
                val confidence = confidencePoints
                    .map { minOf(it.visibility, it.presence) }
                    .average()
                    .toFloat()

                return BodyLandmarks(
                    faceCenter = faceCenter,
                    leftShoulder = leftShoulder,
                    rightShoulder = rightShoulder,
                    hipCenter = hips,
                    confidence = confidence,
                    leftEyeCenter = leftEye,
                    rightEyeCenter = rightEye,
                )
            }
        }
    }

    private fun estimateScreenDistance(
        body: BodyLandmarks,
        calibration: CalibrationProfile?,
        inputImageWidth: Int?,
        inputImageHeight: Int?,
    ): DistanceEstimate? {
        val width = inputImageWidth?.takeIf { it > 0 } ?: return null
        val height = inputImageHeight?.takeIf { it > 0 } ?: return null
        val leftEye = body.leftEyeCenter ?: return null
        val rightEye = body.rightEyeCenter ?: return null
        val calibrationConstant = calibration?.distanceConstantK
            ?.takeIf { it > 0f }
            ?: DistanceCalculator.DEFAULT_CALIBRATION_CONSTANT

        val eyeEstimate = distanceCalculator.estimateDistanceCm(
            leftEye = leftEye.toPixelPoint(width, height),
            rightEye = rightEye.toPixelPoint(width, height),
            calibrationConstantK = calibrationConstant,
        )
        val shoulderDistanceCm = estimateShoulderDistanceCm(body, calibration)
        return when {
            eyeEstimate == null -> null
            shoulderDistanceCm != null &&
                shoulderDistanceCm < eyeEstimate.distanceCm - SHOULDER_DISTANCE_OVERRIDE_MARGIN_CM ->
                eyeEstimate.copy(
                    distanceCm = shoulderDistanceCm,
                    rawDistanceCm = minOf(eyeEstimate.rawDistanceCm, shoulderDistanceCm),
                )
            else -> eyeEstimate
        }
    }

    fun zoneForOrientation(angleDegrees: Int): PostureZone = when {
        angleDegrees < ORIENTATION_WARNING_DEGREES -> PostureZone.SAFE
        angleDegrees < ORIENTATION_DANGER_DEGREES -> PostureZone.WARNING
        else -> PostureZone.DANGER
    }

    private fun estimateShoulderDistanceCm(
        body: BodyLandmarks,
        calibration: CalibrationProfile?,
    ): Int? {
        val baseline = calibration?.shoulderWidth?.takeIf { it > 0.05f } ?: return null
        val current = distance2d(body.leftShoulder, body.rightShoulder).takeIf { it > 0.05f } ?: return null
        return (DistanceCalculator.DEFAULT_CALIBRATION_DISTANCE_CM * baseline / current)
            .roundToInt()
            .coerceIn(10, 120)
    }

    private fun List<LandmarkPoint>.tracked(index: Int): LandmarkPoint? {
        val point = getOrNull(index) ?: return null
        return point.takeIf {
            it.visibility >= MIN_TRACKING_CONFIDENCE && it.presence >= MIN_TRACKING_CONFIDENCE &&
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
        }
    }

    private fun weightedCenter(points: List<Pair<LandmarkPoint, Float>>): LandmarkPoint? {
        if (points.isEmpty()) return null
        val totalWeight = points.sumOf { it.second.toDouble() }.toFloat()
        return LandmarkPoint(
            x = points.sumOf { (point, weight) -> (point.x * weight).toDouble() }.toFloat() / totalWeight,
            y = points.sumOf { (point, weight) -> (point.y * weight).toDouble() }.toFloat() / totalWeight,
            z = points.sumOf { (point, weight) -> (point.z * weight).toDouble() }.toFloat() / totalWeight,
            visibility = points.sumOf { (point, weight) -> (point.visibility * weight).toDouble() }.toFloat() / totalWeight,
            presence = points.sumOf { (point, weight) -> (point.presence * weight).toDouble() }.toFloat() / totalWeight,
        )
    }

    private fun midpoint(a: LandmarkPoint, b: LandmarkPoint): LandmarkPoint =
        weightedCenter(listOf(a to 1f, b to 1f)) ?: a

    private fun distance2d(a: LandmarkPoint, b: LandmarkPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun LandmarkPoint.toPixelPoint(width: Int, height: Int): PixelCoordinate =
        PixelCoordinate(x * width, y * height)

    private fun pairAngleDegrees(a: LandmarkPoint, b: LandmarkPoint): Float =
        Math.toDegrees(
            atan2(abs(b.y - a.y).toDouble(), abs(b.x - a.x).toDouble()),
        ).toFloat()
}
