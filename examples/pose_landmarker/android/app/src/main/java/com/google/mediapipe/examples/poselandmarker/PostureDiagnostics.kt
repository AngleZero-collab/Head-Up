package com.google.mediapipe.examples.poselandmarker

/** Debug-only numeric telemetry. No images, landmarks or account identifiers. */
object PostureDiagnostics {
    private var lastLogMs = 0L
    @Synchronized
    fun record(metrics: PostureMetrics?, inferenceMs: Long) {
        if (!BuildConfig.DEBUG) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastLogMs < 500L) return
        lastLogMs = now
        android.util.Log.d("HeadUpAccuracy", if (metrics == null) {
            "t=$now state=UNAVAILABLE inferenceMs=$inferenceMs"
        } else {
            "t=$now zone=${metrics.zone} pitch=${metrics.headPitchDegrees} " +
                "yaw=${metrics.headYawDegrees} roll=${metrics.headRollDegrees} " +
                "forward=${metrics.relativeAngleDegrees} neck=${metrics.neckFlexionDegrees} " +
                "tilt=${metrics.deviceTiltDegrees} flat=${metrics.isDeviceFlat} " +
                "distance=${metrics.screenDistanceCm} confidence=${metrics.landmarkConfidence} " +
                "inferenceMs=$inferenceMs"
        })
    }
}
