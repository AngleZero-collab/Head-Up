import ActivityKit
import AppIntents
import Foundation

struct PauseMonitoringIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Pause monitoring"
    static var openAppWhenRun = false
    @Parameter(title: "Monitoring session") var sessionID: String
    init() {}
    init(sessionID: String) { self.sessionID = sessionID }

    @MainActor
    func perform() async throws -> some IntentResult {
        #if !HEADUP_WIDGET
        if let id = UUID(uuidString: sessionID) {
            await MonitoringIntentRouter.shared.pause(sessionID: id)
        }
        #endif
        return .result()
    }
}

#if !HEADUP_WIDGET
@MainActor
final class MonitoringIntentRouter {
    static let shared = MonitoringIntentRouter()
    var handler: ((UUID) async -> Void)?
    func pause(sessionID: UUID) async {
        if let handler { await handler(sessionID) }
        // Also works on a cold launch without creating a coordinator or camera.
        for activity in Activity<PostureActivityAttributes>.activities where activity.attributes.sessionID == sessionID {
            var final = activity.content.state
            final.status = .ended
            await activity.end(ActivityContent(state: final, staleDate: nil),
                               dismissalPolicy: .after(Date().addingTimeInterval(900)))
        }
    }
}
#endif
