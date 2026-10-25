import SwiftUI

@main
struct HeadUpApp: App {
    var body: some Scene {
        WindowGroup { HeadUpBootstrap() }
    }
}

private struct HeadUpBootstrap: View {
    @State private var store: HeadUpStore?
    @State private var error: String?
    var body: some View {
        Group {
            if let store {
                HeadUpContainer(store: store)
            } else if let error {
                ContentUnavailableView {
                    Label("Unable to open HeadUp / 無法開啟", systemImage: "externaldrive.badge.exclamationmark")
                } description: {
                    Text(error)
                } actions: {
                    Button("Try again / 重試") { openStore() }
                }
            } else { ProgressView("HeadUp").task { openStore() } }
        }.preferredColorScheme(.dark)
    }
    private func openStore() {
        do {
            store = try HeadUpStore(inMemory: ProcessInfo.processInfo.arguments.contains("--uitesting"))
            error = nil
        } catch { self.error = error.localizedDescription }
    }
}

private struct HeadUpContainer: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var store: HeadUpStore
    @StateObject private var monitor: MonitoringCoordinator
    init(store: HeadUpStore) {
        _store = StateObject(wrappedValue: store)
        _monitor = StateObject(wrappedValue: MonitoringCoordinator(store: store))
    }
    var body: some View {
        HeadUpRootView(store: store, monitor: monitor)
            .onChange(of: scenePhase) { _, phase in monitor.setSceneActive(phase == .active) }
    }
}
