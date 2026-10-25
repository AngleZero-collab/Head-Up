import Foundation

public struct LandmarkPoint: Codable, Equatable, Sendable {
    public let x: Float
    public let y: Float
    public let z: Float
    public let visibility: Float
    public let presence: Float

    public init(x: Float, y: Float, z: Float = 0, visibility: Float = 1, presence: Float = 1) {
        self.x = x
        self.y = y
        self.z = z
        self.visibility = visibility
        self.presence = presence
    }
}

public enum PostureZone: String, Codable, CaseIterable, Sendable {
    case safe, warning, danger
}

public struct CalibrationProfile: Codable, Equatable, Sendable {
    public let angleDegrees: Float
    public let postureRatio: Float
    public let shoulderWidth: Float
    public let calibratedAtMs: Int64

    public init(angleDegrees: Float, postureRatio: Float, shoulderWidth: Float, calibratedAtMs: Int64 = 0) {
        self.angleDegrees = angleDegrees
        self.postureRatio = postureRatio
        self.shoulderWidth = shoulderWidth
        self.calibratedAtMs = calibratedAtMs
    }
}

public struct PostureMetrics: Codable, Equatable, Sendable {
    public let angleDegrees: Int
    public let zone: PostureZone
    /// The smoothed, uncalibrated head angle, matching Android's rawAngleDegrees field.
    public let rawAngleDegrees: Float
    public let relativeAngleDegrees: Int
    public let neckFlexionDegrees: Int
    public let shoulderBalanceDegrees: Int
    public let postureRatio: Float
    public let shoulderWidth: Float
    public let screenDistanceCm: Int?
    public let isTooClose: Bool
    public let landmarkConfidence: Float
    public let deviceTiltDegrees: Int
    public let isDeviceFlat: Bool
    public let isRapidFall: Bool
    /// Wall-clock time for storage; alert timing uses a separate monotonic clock.
    public let timestampMs: Int64

    public var isGoodPosture: Bool { zone == .safe }

    public init(
        angleDegrees: Int,
        zone: PostureZone,
        timestampMs: Int64,
        rawAngleDegrees: Float? = nil,
        relativeAngleDegrees: Int? = nil,
        neckFlexionDegrees: Int = 0,
        shoulderBalanceDegrees: Int = 0,
        postureRatio: Float = 0,
        shoulderWidth: Float = 0,
        screenDistanceCm: Int? = nil,
        isTooClose: Bool = false,
        landmarkConfidence: Float = 0,
        deviceTiltDegrees: Int = 0,
        isDeviceFlat: Bool = false,
        isRapidFall: Bool = false
    ) {
        self.angleDegrees = angleDegrees
        self.zone = zone
        self.timestampMs = timestampMs
        self.rawAngleDegrees = rawAngleDegrees ?? Float(angleDegrees)
        self.relativeAngleDegrees = relativeAngleDegrees ?? angleDegrees
        self.neckFlexionDegrees = neckFlexionDegrees
        self.shoulderBalanceDegrees = shoulderBalanceDegrees
        self.postureRatio = postureRatio
        self.shoulderWidth = shoulderWidth
        self.screenDistanceCm = screenDistanceCm
        self.isTooClose = isTooClose
        self.landmarkConfidence = landmarkConfidence
        self.deviceTiltDegrees = deviceTiltDegrees
        self.isDeviceFlat = isDeviceFlat
        self.isRapidFall = isRapidFall
    }
}
