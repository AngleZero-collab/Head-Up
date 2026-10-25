import SwiftUI
import AVFoundation
import HeadUpCore

struct ChallengeScreen: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    @State private var confirmsEyeRest = false
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        HeadUpPage {
            HeadUpSectionTitle(title: copy.text("Small habits, brighter days", "每天一點，照顧自己"), symbol: "sparkles")
            HeadUpCard {
                VStack(spacing: 18) {
                    HStack {
                        Text(copy.text("My Vision Dragon", "我的視力龍")).font(.title3.bold())
                        Spacer()
                        Text("Lv. \(store.dragonLevel)")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(HeadUpStyle.purple.opacity(0.2), in: Capsule())
                    }
                    DragonVideoView(resource: monitor.metrics?.zone == .danger ? "angry_dragon" : "blue_dragon", copy: copy)
                        .frame(width: 220, height: 220)
                        .clipShape(Circle())
                        .overlay(Circle().strokeBorder(HeadUpStyle.color(for: monitor.metrics?.zone).opacity(0.45), lineWidth: 2))
                        .shadow(color: HeadUpStyle.cyan.opacity(0.15), radius: 25)
                    Text("Little Blue").font(.title.bold())
                    ProgressView(value: Double(store.dragonEnergy), total: 100)
                        .tint(HeadUpStyle.cyan)
                        .accessibilityLabel(copy.text("Dragon energy", "視力龍能量"))
                    Text(copy.text("Energy \(store.dragonEnergy)%", "能量 \(store.dragonEnergy)%"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                    Text(dragonMood)
                        .font(.headline)
                        .foregroundStyle(HeadUpStyle.color(for: monitor.metrics?.zone))
                }
                .frame(maxWidth: .infinity)
            }
            HeadUpCard {
                VStack(alignment: .leading, spacing: 14) {
                    Label(copy.text("20-20-20 eye break", "20-20-20 護眼休息"), systemImage: "eye")
                        .font(.headline)
                        .foregroundStyle(HeadUpStyle.cyan)
                    Text(copy.text("Every 20 minutes, look about 20 feet away for 20 seconds.", "每使用螢幕 20 分鐘，望向約 6 公尺遠處，讓眼睛休息 20 秒。"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                    HStack {
                        Text(copy.text("Today: \(store.today.eyeRestCount) breaks", "今日已休息 \(store.today.eyeRestCount) 次"))
                            .font(.subheadline).monospacedDigit()
                        Spacer()
                        Button(copy.text("Record break", "紀錄休息")) { confirmsEyeRest = true }
                            .buttonStyle(.borderedProminent)
                            .foregroundStyle(HeadUpStyle.top)
                            .accessibilityIdentifier("recordEyeRest")
                    }
                }
            }
            HeadUpSectionTitle(title: copy.text("Today's challenges", "今日挑戰"), symbol: "flag.checkered")
            ForEach(store.tasks) { task in
                HeadUpTaskRow(store: store, task: task, copy: copy)
            }
        }
        .accessibilityIdentifier("challengeScreen")
        .alert(copy.text("Give your eyes a break", "讓眼睛休息一下"), isPresented: $confirmsEyeRest) {
            Button(copy.text("I've finished my break", "我已完成休息")) { store.recordEyeRest() }
            Button(copy.text("Not yet", "稍後再記錄"), role: .cancel) { }
        } message: {
            Text(copy.text("Look into the distance and relax your eyes. Confirm after you finish your break.", "請望向遠方，放鬆眼睛。完成休息後再按下確認。"))
        }
    }

    private var dragonMood: String {
        switch monitor.metrics?.zone {
        case .safe: return copy.text("Little Blue is recharging", "Little Blue 正在充電")
        case .warning: return copy.text("Little Blue reminds you to look up", "Little Blue 提醒你抬頭")
        case .danger: return copy.text("Little Blue needs a little rest", "Little Blue 需要休息")
        case nil: return copy.text("Little Blue is waiting for you", "Little Blue 正在等你")
        }
    }
}

private final class DragonPlayback: ObservableObject {
    let player = AVQueuePlayer()
    private var looper: AVPlayerLooper?
    private var resource: String?

    func load(_ name: String) {
        guard resource != name else { return }
        player.pause()
        looper?.disableLooping()
        player.removeAllItems()
        resource = name
        guard let url = Bundle.main.url(forResource: name, withExtension: "mp4") else { return }
        player.isMuted = true
        looper = AVPlayerLooper(player: player, templateItem: AVPlayerItem(url: url))
    }

    deinit { player.pause() }
}

private struct DragonVideoView: View {
    let resource: String
    let copy: HeadUpCopy
    @StateObject private var playback = DragonPlayback()
    @State private var userPaused = false
    @State private var visible = false
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button {
            userPaused.toggle()
            updatePlayback()
        } label: {
            ZStack(alignment: .bottomTrailing) {
                DragonPlayerLayer(player: playback.player)
                    .background(HeadUpStyle.top)
                Image(systemName: userPaused || reduceMotion ? "play.fill" : "pause.fill")
                    .font(.caption.bold())
                    .padding(10)
                    .background(.ultraThinMaterial, in: Circle())
                    .padding(24)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(copy.text("Little Blue animation", "Little Blue 動畫"))
        .accessibilityHint(copy.text("Tap to pause or play", "點一下暫停或播放"))
        .onAppear {
            visible = true
            playback.load(resource)
            updatePlayback()
        }
        .onDisappear {
            visible = false
            playback.player.pause()
        }
        .onChange(of: resource) { _, name in playback.load(name); updatePlayback() }
        .onChange(of: scenePhase) { _, _ in updatePlayback() }
        .onChange(of: reduceMotion) { _, _ in updatePlayback() }
    }

    private func updatePlayback() {
        if visible && scenePhase == .active && !userPaused && !reduceMotion { playback.player.play() }
        else { playback.player.pause() }
    }
}

private struct DragonPlayerLayer: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerView {
        let view = PlayerView()
        view.playerLayer.videoGravity = .resizeAspectFill
        view.playerLayer.player = player
        return view
    }

    func updateUIView(_ uiView: PlayerView, context: Context) { uiView.playerLayer.player = player }

    static func dismantleUIView(_ uiView: PlayerView, coordinator: ()) { uiView.playerLayer.player = nil }

    final class PlayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }
}
