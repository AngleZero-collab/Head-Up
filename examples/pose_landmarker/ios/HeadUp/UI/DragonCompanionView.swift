import ImageIO
import SwiftUI
import UIKit

/// Only the visible, foreground Challenge page animates. PiP and Live Activities
/// use stills so camera inference does not compete with background animation.
struct DragonCompanionView: View {
    let status: PostureActivityAttributes.Status
    let copy: HeadUpCopy
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var userPaused = false
    @State private var frame: UIImage?
    @State private var frameResource: String?

    private var resource: String { status == .liftHead ? "dragon_angry_red" : "dragon_happy_blue" }
    private var shouldAnimate: Bool { status.hasLivePose && scenePhase == .active && !reduceMotion && !userPaused }

    var body: some View {
        Button { userPaused.toggle() } label: {
            ZStack(alignment: .bottomTrailing) {
                PostureDragonView(status: status, size: 220)
                    .opacity(shouldAnimate && frame != nil && frameResource == resource ? 0 : 1)
                if shouldAnimate, frameResource == resource, let frame {
                    Image(uiImage: frame).resizable().scaledToFit()
                        .frame(width: 220, height: 220)
                }
                if status.hasLivePose && !reduceMotion {
                    Image(systemName: userPaused ? "play.fill" : "pause.fill")
                        .font(.caption.bold()).padding(10)
                        .background(.ultraThinMaterial, in: Circle())
                }
            }
            .frame(width: 220, height: 220)
        }
        .buttonStyle(.plain)
        .disabled(!status.hasLivePose || reduceMotion)
        .accessibilityLabel(copy.text("Vision Dragon animation", "視力龍動畫"))
        .accessibilityValue(copy.text(shouldAnimate ? "Playing" : "Paused", shouldAnimate ? "播放中" : "已暫停"))
        .accessibilityHint(copy.text("Tap to pause or play", "點一下暫停或播放"))
        .task(id: shouldAnimate ? resource : nil) {
            frame = nil
            frameResource = nil
            guard shouldAnimate,
                  let url = Bundle.main.url(forResource: resource, withExtension: "webp"),
                  let source = CGImageSourceCreateWithURL(url as CFURL,
                      [kCGImageSourceShouldCache: false] as CFDictionary) else { return }
            let count = CGImageSourceGetCount(source)
            guard count > 1 else { return } // Keep the supplied PNG if decoding is unavailable.
            var index = 0
            while !Task.isCancelled {
                guard let image = CGImageSourceCreateImageAtIndex(source, index,
                    [kCGImageSourceShouldCache: false] as CFDictionary) else { return }
                frame = UIImage(cgImage: image)
                frameResource = resource
                let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any]
                let webp = properties?[kCGImagePropertyWebPDictionary] as? [CFString: Any]
                let delay = (webp?[kCGImagePropertyWebPUnclampedDelayTime] as? NSNumber)?.doubleValue
                    ?? (webp?[kCGImagePropertyWebPDelayTime] as? NSNumber)?.doubleValue ?? 1.0 / 12
                // Retain one displayed frame, rather than decoding the entire movie into RAM.
                do { try await Task.sleep(for: .seconds(max(1.0 / 15, min(1, delay)))) }
                catch { return }
                index = (index + 1) % count
            }
        }
        .onDisappear { frame = nil; frameResource = nil }
    }
}
