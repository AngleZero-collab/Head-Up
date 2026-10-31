import SwiftUI

extension PostureActivityAttributes.Status {
    var hasLivePose: Bool { [.good, .attention, .liftHead].contains(self) }
    var dragonAsset: String { self == .liftHead ? "dragonAngry" : "dragonBlue" }

    var dragonTint: Color {
        switch self {
        case .good: return Color(red: 56 / 255, green: 200 / 255, blue: 244 / 255)
        case .attention: return Color(red: 255 / 255, green: 209 / 255, blue: 102 / 255)
        case .liftHead: return Color(red: 255 / 255, green: 107 / 255, blue: 107 / 255)
        default: return .gray
        }
    }
}

/// The same supplied artwork and state mapping appear in the app, PiP and widget.
/// Decorations are silent to VoiceOver; the enclosing status supplies its label.
struct PostureDragonView: View {
    let status: PostureActivityAttributes.Status
    var isStale = false
    let size: CGFloat

    private var outdated: Bool { isStale && status.requiresFreshness }
    private var active: Bool { status.hasLivePose && !outdated }
    private var angry: Bool { status == .liftHead && active }

    var body: some View {
        Image(angry ? "dragonAngry" : "dragonBlue")
            .resizable().scaledToFit()
            .frame(width: size, height: size)
            // The supplied angry PNG includes more transparent padding.
            // Normalize its display size without altering either original asset.
            .scaleEffect(angry ? 1.75 : 1)
            .frame(width: size, height: size)
            .clipped()
            .saturation(active ? 1 : 0)
            .opacity(active ? 1 : 0.55)
            .overlay(alignment: .bottomTrailing) {
                Image(systemName: outdated ? "clock.badge.exclamationmark" : status.symbol)
                    .font(.system(size: max(9, min(22, size * 0.28)), weight: .bold))
                    .foregroundStyle(outdated ? .gray : status.dragonTint)
                    .background(.black.opacity(0.85), in: Circle())
            }
            .accessibilityHidden(true)
    }
}
