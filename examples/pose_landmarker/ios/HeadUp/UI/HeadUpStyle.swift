import SwiftUI
import HeadUpCore

enum HeadUpStyle {
    static let top = Color(red: 7 / 255, green: 20 / 255, blue: 38 / 255)
    static let bottom = Color(red: 16 / 255, green: 22 / 255, blue: 51 / 255)
    static let card = Color(red: 27 / 255, green: 40 / 255, blue: 72 / 255)
    static let cyan = Color(red: 56 / 255, green: 200 / 255, blue: 244 / 255)
    static let purple = Color(red: 169 / 255, green: 136 / 255, blue: 255 / 255)
    static let secondary = Color(red: 185 / 255, green: 196 / 255, blue: 216 / 255)
    static let safe = Color(red: 144 / 255, green: 232 / 255, blue: 160 / 255)
    static let warning = Color(red: 255 / 255, green: 209 / 255, blue: 102 / 255)
    static let danger = Color(red: 255 / 255, green: 107 / 255, blue: 107 / 255)

    static func color(for zone: PostureZone?) -> Color {
        switch zone {
        case .safe: return safe
        case .warning: return warning
        case .danger: return danger
        case nil: return secondary
        }
    }
}

/// Explicit language selection updates every screen immediately, including chart labels.
struct HeadUpCopy {
    let language: AppLanguage

    var isChinese: Bool {
        switch language {
        case .traditionalChinese: return true
        case .english: return false
        case .system: return Locale.preferredLanguages.first?.hasPrefix("zh") == true
        }
    }

    var locale: Locale { Locale(identifier: isChinese ? "zh_TW" : "en") }

    func text(_ english: String, _ chinese: String) -> String {
        isChinese ? chinese : english
    }

    func zone(_ zone: PostureZone?) -> String {
        switch zone {
        case .safe: return text("Good posture", "姿勢良好")
        case .warning: return text("Time to lift your head", "請慢慢抬頭")
        case .danger: return text("Lift your head and rest", "請抬頭休息")
        case nil: return text("Waiting for your posture", "等待偵測姿勢")
        }
    }

    func duration(_ seconds: Int64) -> String {
        let minutes = max(0, seconds) / 60
        if minutes >= 60 {
            return text("\(minutes / 60) hr \(minutes % 60) min", "\(minutes / 60) 小時 \(minutes % 60) 分")
        }
        return text("\(minutes) min", "\(minutes) 分鐘")
    }

    func taskTitle(_ id: String) -> String {
        switch id {
        case "good_posture": return text("30 minutes of good posture", "維持正確姿勢 30 分鐘")
        case "eye_rest": return text("Take 3 eye breaks", "完成 3 次護眼休息")
        default: return text("Complete the posture challenge", "完成姿勢挑戰")
        }
    }

    func taskDetail(_ id: String) -> String {
        switch id {
        case "good_posture": return text("Good posture time adds up throughout the day.", "累積今天的正確姿勢時間。")
        case "eye_rest": return text("Look into the distance, then record your break.", "望向遠方讓眼睛休息，再記錄完成。")
        default: return text("Track 10 minutes with at least 80% good posture.", "累積偵測 10 分鐘，正確姿勢比例達 80%。")
        }
    }
}

struct HeadUpCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(HeadUpStyle.card.opacity(0.86), in: RoundedRectangle(cornerRadius: 24))
            .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(.white.opacity(0.08)))
    }
}

struct HeadUpPage<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) { content }
                .frame(maxWidth: 1050)
                .padding(.horizontal, 20)
                .padding(.vertical, 20)
                .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .background(LinearGradient(colors: [HeadUpStyle.top, HeadUpStyle.bottom], startPoint: .top, endPoint: .bottom))
    }
}

struct HeadUpSectionTitle: View {
    let title: String
    let symbol: String

    var body: some View {
        Label(title, systemImage: symbol)
            .font(.title2.bold())
            .foregroundStyle(.white)
            .symbolRenderingMode(.hierarchical)
            .accessibilityAddTraits(.isHeader)
    }
}

struct HeadUpStatTile: View {
    let title: String
    let value: String
    let symbol: String
    var color: Color = HeadUpStyle.cyan

    var body: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 12) {
                Image(systemName: symbol).font(.title3).foregroundStyle(color)
                Text(value).font(.title2.bold()).foregroundStyle(.white).monospacedDigit()
                Text(title).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
