import Foundation

/// Stateful Android-compatible analysis. Use one instance on one serial executor per camera stream.
public final class PostureAnalyzer {
    private var smoothedAngle: Float?
    private let alpha: Float = 0.15

    public init() {}

    public func reset() { smoothedAngle = nil }

    public static func zone(forAngleDegrees angle: Int) -> PostureZone {
        if angle >= 25 { return .danger }
        if angle >= 15 { return .warning }
        return .safe
    }

    public func analyze(
        landmarks: [LandmarkPoint],
        deviceTiltDegrees: Int = 0,
        isDeviceFlat: Bool = false,
        calibration: CalibrationProfile? = nil,
        timestampMs: Int64
    ) -> PostureMetrics? {
        guard let leftShoulder = tracked(11, in: landmarks),
              let rightShoulder = tracked(12, in: landmarks),
              let face = faceCenter(in: landmarks) else {
            reset()
            return nil
        }
        let width = Float(hypot(Double(leftShoulder.x - rightShoulder.x), Double(leftShoulder.y - rightShoulder.y)))
        guard width > 0.001, width.isFinite else {
            reset()
            return nil
        }
        let shoulderCenter = weightedCenter([(leftShoulder, 1), (rightShoulder, 1)])!
        let verticalDistance = max(shoulderCenter.y - face.center.y, 0.001)
        let ratio = verticalDistance / width
        guard verticalDistance.isFinite, ratio.isFinite,
              shoulderCenter.z.isFinite, face.center.z.isFinite else {
            reset()
            return nil
        }
        let rawAngle = min(max(Float(atan2(Double(shoulderCenter.z - face.center.z), Double(verticalDistance)) * 180 / .pi), 0), 90)
        let smoothed = smoothedAngle.map { alpha * rawAngle + (1 - alpha) * $0 } ?? rawAngle
        let velocity = smoothedAngle.map { smoothed - $0 } ?? 0
        smoothedAngle = smoothed

        // Invalid imported profiles must not propagate NaN into integer conversions.
        let profile = calibration.flatMap { profile in
            profile.angleDegrees.isFinite && (0...90).contains(profile.angleDegrees)
                && profile.postureRatio.isFinite && profile.postureRatio >= 0
                && profile.shoulderWidth.isFinite && profile.shoulderWidth >= 0 ? profile : nil
        }
        let relativeAngle = max(smoothed - (profile?.angleDegrees ?? 0), 0)
        let baseline: Float = (profile?.postureRatio ?? 0) > 0.05 ? profile!.postureRatio : 0.82
        let compression = max((baseline - ratio) / baseline, 0)
        let neckFlexion = min(max(compression * 110, 0), 60)
        let angle = roundedInt(max(relativeAngle, neckFlexion))
        let shoulderBalance = Float(atan2(Double(abs(rightShoulder.y - leftShoulder.y)), Double(abs(rightShoulder.x - leftShoulder.x))) * 180 / .pi)

        let distance: Int? = profile.flatMap { profile in
            guard profile.shoulderWidth > 0.001 else { return nil }
            // Clamp before conversion so an unusual but finite imported calibration cannot overflow Int.
            return roundedInt(min(max(45 * profile.shoulderWidth / width, 15), 120))
        }
        let tooClose = distance.map { $0 < 35 } ?? false
        let zone: PostureZone
        if angle >= 25 || (distance.map { $0 < 25 } ?? false) || shoulderBalance >= 14 {
            zone = .danger
        } else if angle >= 15 || tooClose || shoulderBalance >= 8 {
            zone = .warning
        } else {
            zone = .safe
        }
        let confidencePoints = face.parts + [leftShoulder, rightShoulder]
        let confidence = Float(confidencePoints.reduce(0.0) { $0 + Double(min($1.visibility, $1.presence)) } / Double(confidencePoints.count))

        return PostureMetrics(
            angleDegrees: angle,
            zone: zone,
            timestampMs: timestampMs,
            rawAngleDegrees: smoothed,
            relativeAngleDegrees: roundedInt(relativeAngle),
            neckFlexionDegrees: roundedInt(neckFlexion),
            shoulderBalanceDegrees: roundedInt(shoulderBalance),
            postureRatio: ratio,
            shoulderWidth: width,
            screenDistanceCm: distance,
            isTooClose: tooClose,
            landmarkConfidence: confidence,
            deviceTiltDegrees: deviceTiltDegrees,
            isDeviceFlat: isDeviceFlat,
            isRapidFall: angle >= 22 && velocity > 0.5
        )
    }

    private func faceCenter(in points: [LandmarkPoint]) -> (center: LandmarkPoint, parts: [LandmarkPoint])? {
        let eyes = weightedCenter([(1, Float(1)), (2, 1.25), (3, 1), (4, 1), (5, 1.25), (6, 1)].compactMap { index, weight in
            tracked(index, in: points).map { ($0, weight) }
        })
        let ears = weightedCenter([7, 8].compactMap { tracked($0, in: points).map { ($0, Float(1)) } })
        let mouth = weightedCenter([9, 10].compactMap { tracked($0, in: points).map { ($0, Float(1)) } })
        let candidates: [(LandmarkPoint?, Float)] = [(tracked(0, in: points), 3), (eyes, 2.5), (ears, 1.5), (mouth, 1.5)]
        let parts = candidates.compactMap { point, weight in point.map { ($0, weight) } }
        guard let center = weightedCenter(parts) else { return nil }
        return (center, parts.map(\.0))
    }

    private func tracked(_ index: Int, in points: [LandmarkPoint]) -> LandmarkPoint? {
        guard points.indices.contains(index) else { return nil }
        let point = points[index]
        guard point.visibility >= 0.20, point.presence >= 0.20,
              point.x.isFinite, point.y.isFinite, point.z.isFinite,
              point.visibility.isFinite, point.presence.isFinite else { return nil }
        return point
    }

    private func weightedCenter(_ points: [(LandmarkPoint, Float)]) -> LandmarkPoint? {
        guard !points.isEmpty else { return nil }
        let total = Float(points.reduce(0.0) { $0 + Double($1.1) })
        func average(_ keyPath: KeyPath<LandmarkPoint, Float>) -> Float {
            Float(points.reduce(0.0) { $0 + Double($1.0[keyPath: keyPath] * $1.1) }) / total
        }
        return LandmarkPoint(x: average(\.x), y: average(\.y), z: average(\.z), visibility: average(\.visibility), presence: average(\.presence))
    }

    private func roundedInt(_ value: Float) -> Int {
        // All converted values are nonnegative; this matches Kotlin Float.roundToInt's half-up rule.
        Int(value.rounded(.toNearestOrAwayFromZero))
    }
}
