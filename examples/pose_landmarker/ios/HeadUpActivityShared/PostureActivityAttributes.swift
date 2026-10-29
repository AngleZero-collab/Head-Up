import ActivityKit
import Foundation

/// Shared value snapshots only. The widget does not open the app's store or camera.
struct PostureActivityAttributes: ActivityAttributes {
    var sessionID: UUID

    enum Status: String, Codable, Hashable {
        case good, attention, liftHead, starting, calibrating, noPose, paused, failed, ended

        var requiresFreshness: Bool {
            switch self {
            case .paused, .failed, .ended: return false
            default: return true
            }
        }

        var symbol: String {
            switch self {
            case .good: return "checkmark.circle.fill"
            case .attention: return "exclamationmark.circle.fill"
            case .liftHead: return "arrow.up.circle.fill"
            case .starting: return "camera"
            case .calibrating: return "scope"
            case .noPose: return "person.crop.rectangle"
            case .paused, .ended: return "pause.circle.fill"
            case .failed: return "camera.badge.ellipsis"
            }
        }
    }

    struct ContentState: Codable, Hashable {
        var status: Status
        var safeSeconds: Int64
        var totalSeconds: Int64
        var dayStart: Date
        var updatedAt: Date
        var language: String

        var isChinese: Bool { language.hasPrefix("zh") }
        func text(_ english: String, _ chinese: String) -> String { isChinese ? chinese : english }

        var goodPercent: Int? {
            guard totalSeconds > 0 else { return nil }
            return Int(min(totalSeconds, max(0, safeSeconds)) * 100 / totalSeconds)
        }

        var percentText: String { goodPercent.map { "\($0)%" } ?? "—" }
        var trackedDuration: String {
            let minutes = max(0, totalSeconds) / 60
            if minutes >= 60 { return text("\(minutes / 60)h \(minutes % 60)m", "\(minutes / 60) 小時 \(minutes % 60) 分") }
            return text("\(minutes)m", "\(minutes) 分鐘")
        }

        var goalText: String {
            if totalSeconds == 0 { return text("No records yet", "尚無紀錄") }
            if totalSeconds < 600 { return text("Building your day", "累積中") }
            return goodPercent ?? 0 >= 80 ? text("Goal reached", "已達標") : text("Keep going", "繼續加油")
        }

        func dayLabel(now: Date = Date(), calendar: Calendar = .current) -> String {
            guard calendar.isDate(dayStart, inSameDayAs: now) else {
                let formatter = DateFormatter()
                formatter.locale = Locale(identifier: isChinese ? "zh_TW" : "en_US")
                formatter.dateFormat = isChinese ? "M月d日" : "MMM d"
                return formatter.string(from: dayStart)
            }
            return text("Today", "今日")
        }

        func isOutdated(_ isStale: Bool) -> Bool { isStale && status.requiresFreshness }

        func compactTitle(isStale: Bool) -> String {
            if isOutdated(isStale) { return text("Stale", "未更新") }
            switch status {
            case .good: return text("Good", "良好")
            case .attention: return text("Careful", "注意")
            case .liftHead: return text("Look up", "抬頭")
            case .starting: return text("Starting", "啟動中")
            case .calibrating: return text("Calibrate", "校準中")
            case .noPose: return text("No pose", "未偵測")
            case .paused, .ended: return text("Paused", "已暫停")
            case .failed: return text("Unavailable", "無法偵測")
            }
        }

        func statusTitle(isStale: Bool) -> String {
            if isOutdated(isStale) { return text("Data has not updated", "資料未更新") }
            switch status {
            case .good: return text("Good posture", "目前姿勢良好")
            case .attention: return text("Check your posture", "請注意目前姿勢")
            case .liftHead: return text("Lift your head", "請抬頭調整姿勢")
            case .starting: return text("Starting camera", "正在啟動相機")
            case .calibrating: return text("Calibrating posture", "正在校準姿勢")
            case .noPose: return text("Keep face and shoulders in view", "請讓臉部與雙肩入鏡")
            case .paused: return text("Monitoring paused", "偵測已暫停")
            case .failed: return text("Camera unavailable", "相機無法偵測")
            case .ended: return text("Monitoring ended", "本次偵測已結束")
            }
        }
    }
}
