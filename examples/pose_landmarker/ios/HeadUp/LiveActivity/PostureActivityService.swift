import ActivityKit
import Foundation

@MainActor
protocol PostureActivityClient {
    var enabled: Bool { get }
    var existingIDs: [String] { get }
    func isActive(_ id: String) -> Bool
    func request(sessionID: UUID, content: PostureActivityAttributes.ContentState) throws -> String
    func update(_ id: String, content: PostureActivityAttributes.ContentState) async
    func end(_ id: String, content: PostureActivityAttributes.ContentState?, immediately: Bool) async
}

@MainActor
final class SystemPostureActivityClient: PostureActivityClient {
    private var retained: [String: Activity<PostureActivityAttributes>] = [:]
    var enabled: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }
    var existingIDs: [String] { Array(Set(Activity<PostureActivityAttributes>.activities.map(\.id)).union(retained.keys)) }
    private func activity(_ id: String) -> Activity<PostureActivityAttributes>? {
        retained[id] ?? Activity<PostureActivityAttributes>.activities.first { $0.id == id }
    }
    func isActive(_ id: String) -> Bool {
        guard let activity = activity(id) else { return false }
        return activity.activityState == .active || activity.activityState == .stale
    }
    func request(sessionID: UUID, content: PostureActivityAttributes.ContentState) throws -> String {
        let activity = try Activity.request(attributes: PostureActivityAttributes(sessionID: sessionID),
                                            content: wrapped(content), pushType: nil)
        retained[activity.id] = activity
        return activity.id
    }
    func update(_ id: String, content: PostureActivityAttributes.ContentState) async {
        await activity(id)?.update(wrapped(content))
    }
    func end(_ id: String, content: PostureActivityAttributes.ContentState?, immediately: Bool) async {
        let activity = activity(id)
        var final = content ?? activity?.content.state
        final?.status = .ended
        await activity?.end(final.map { ActivityContent(state: $0, staleDate: nil) },
                            dismissalPolicy: immediately ? .immediate : .after(Date().addingTimeInterval(900)))
        // Keep the handle to a 15-minute lock-screen summary so Reset/opt-out can
        // remove it even if Activity.activities no longer lists ended activities.
        if immediately { retained[id] = nil }
        else if let activity { retained[id] = activity }
    }
    private func wrapped(_ content: PostureActivityAttributes.ContentState) -> ActivityContent<PostureActivityAttributes.ContentState> {
        // Paused/error states are already explicitly non-live. Active snapshots expire
        // even when the app is suspended and its watchdog cannot execute.
        let stale = [.paused, .failed, .ended].contains(content.status) ? nil : content.updatedAt.addingTimeInterval(10)
        return ActivityContent(state: content, staleDate: stale)
    }
}

/// Serializes ActivityKit writes, coalesces frame traffic, and never resurrects a
/// dismissed activity within the same monitoring session.
@MainActor
final class PostureActivityService {
    private let client: PostureActivityClient
    private let now: () -> Date
    private var tail: Task<Void, Never>?
    private var revision = 0
    private var sessionID: UUID?
    private var suppressedSession: UUID?
    private(set) var activityID: String?
    private var lastContent: PostureActivityAttributes.ContentState?
    private var lastSubmittedAt: Date?
    private(set) var requestFailed = false

    init(client: PostureActivityClient? = nil, now: @escaping () -> Date = Date.init) {
        let client = client ?? SystemPostureActivityClient()
        self.client = client
        self.now = now
        let oldIDs = client.existingIDs
        enqueue { for id in oldIDs { await client.end(id, content: nil, immediately: true) } }
    }

    func offer(sessionID: UUID, content: PostureActivityAttributes.ContentState, canStart: Bool, force: Bool = false) {
        if self.sessionID != sessionID {
            end(immediately: false)
            self.sessionID = sessionID
            suppressedSession = nil
            requestFailed = false
        }
        guard suppressedSession != sessionID else { return }
        if let id = activityID, !client.isActive(id) {
            // User dismissal/system expiry is terminal for this session.
            revision += 1
            activityID = nil
            suppressedSession = sessionID
            return
        }
        guard client.enabled else {
            if activityID != nil { end(immediately: true) }
            suppressedSession = sessionID
            return
        }
        guard activityID != nil || canStart else { return }
        let changed = lastContent?.status != content.status || lastContent?.dayStart != content.dayStart || lastContent?.language != content.language
        let elapsed = lastSubmittedAt.map { now().timeIntervalSince($0) } ?? .infinity
        let urgent = [.paused, .noPose, .failed, .ended, .calibrating].contains(content.status)
        guard force || urgent && changed || changed && elapsed >= 1 || elapsed >= 5 else { return }
        lastContent = content
        lastSubmittedAt = now()
        revision += 1
        let version = revision
        enqueue { [weak self] in
            guard let self, self.revision == version, self.sessionID == sessionID, self.client.enabled else { return }
            if self.activityID == nil {
                guard canStart, self.suppressedSession != sessionID else { return }
                do { self.activityID = try self.client.request(sessionID: sessionID, content: content) }
                catch { self.requestFailed = true; self.suppressedSession = sessionID }
            } else if let id = self.activityID {
                await self.client.update(id, content: content)
            }
        }
    }

    func end(immediately: Bool) {
        revision += 1
        let id = activityID
        let content = lastContent
        activityID = nil
        sessionID = nil
        lastContent = nil
        lastSubmittedAt = nil
        enqueue { [client] in
            if let id { await client.end(id, content: content, immediately: immediately) }
            if immediately {
                // Also remove prior retained lock-screen summaries on reset/opt-out.
                for other in client.existingIDs where other != id {
                    await client.end(other, content: nil, immediately: true)
                }
            }
        }
    }

    func waitForPendingUpdates() async { await tail?.value }

    private func enqueue(_ operation: @escaping @MainActor () async -> Void) {
        let previous = tail
        tail = Task { await previous?.value; await operation() }
    }
}
