import Foundation

public enum AlertHaptic: Equatable, Sendable {
    case rapidFall, danger
}

public struct AlertOutput: Equatable, Sendable {
    public let warningActive: Bool
    public let haptic: AlertHaptic?
    public let shouldPlaySound: Bool

    public init(warningActive: Bool = false, haptic: AlertHaptic? = nil, shouldPlaySound: Bool = false) {
        self.warningActive = warningActive
        self.haptic = haptic
        self.shouldPlaySound = shouldPlaySound
    }
}

/// Pure alert timing; the app owns haptics, sound and foreground-only presentation.
/// Call serially with monotonic milliseconds. A missing pose cancels an active warning.
public final class AlertEngine {
    private var dangerStartedAt: Int64?
    private var warningActive = false
    private var wasRapidFall = false
    private var lastHapticAt: Int64?
    private var lastSoundAt: Int64?
    private var lastTime: Int64?

    public init() {}

    /// Clears posture continuity while preserving output cooldowns across camera interruptions.
    @discardableResult
    public func reset() -> AlertOutput {
        dangerStartedAt = nil
        warningActive = false
        wasRapidFall = false
        return AlertOutput()
    }

    public func process(metrics: PostureMetrics?, monotonicTimeMs now: Int64, alarmEnabled: Bool = false) -> AlertOutput {
        if let lastTime, now < lastTime {
            reset()
            lastHapticAt = nil
            lastSoundAt = nil
        }
        lastTime = now
        guard let metrics else { return reset() }
        var haptic: AlertHaptic?
        var sound = false

        if metrics.zone == .danger {
            if dangerStartedAt == nil { dangerStartedAt = now }
            if let start = dangerStartedAt, now - start >= 2_000, !warningActive {
                warningActive = true
                if canEmit(after: lastHapticAt, now: now, cooldown: 1_500) {
                    haptic = .danger
                    lastHapticAt = now
                }
                if alarmEnabled, canEmit(after: lastSoundAt, now: now, cooldown: 3_000) {
                    sound = true
                    lastSoundAt = now
                }
            }
        } else {
            dangerStartedAt = nil
            warningActive = false
        }

        if metrics.isRapidFall, !wasRapidFall, canEmit(after: lastHapticAt, now: now, cooldown: 1_500) {
            haptic = .rapidFall
            lastHapticAt = now
        }
        wasRapidFall = metrics.isRapidFall
        return AlertOutput(warningActive: warningActive, haptic: haptic, shouldPlaySound: sound)
    }

    private func canEmit(after last: Int64?, now: Int64, cooldown: Int64) -> Bool {
        guard let last else { return true }
        return now - last >= cooldown
    }
}
