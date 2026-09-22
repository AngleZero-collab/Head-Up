package com.google.mediapipe.examples.poselandmarker

import kotlin.math.abs

fun PostureMetrics.orientationFeedbackRes(): Int = when {
    isLowPhonePositionConfirmed -> R.string.phone_too_low_feedback
    isHeadOrientationConfirmed && abs(headRollDegrees ?: 0) >= 7 -> R.string.head_lateral_feedback
    isHeadOrientationConfirmed && (headPitchDegrees ?: 0) <= -5 -> R.string.head_look_up_feedback
    isHeadOrientationConfirmed && abs(headYawDegrees ?: 0) >= 7 -> R.string.head_turn_feedback
    isHeadOrientationConfirmed && abs(headLateralDegrees ?: 0) >= 7 -> R.string.head_lateral_feedback
    isHeadOrientationConfirmed && (headPitchDegrees ?: 0) >= 5 -> R.string.head_look_down_feedback
    zone == PostureZone.DANGER -> R.string.posture_status_danger
    zone == PostureZone.WARNING -> R.string.posture_status_warning
    else -> R.string.posture_status_safe
}
