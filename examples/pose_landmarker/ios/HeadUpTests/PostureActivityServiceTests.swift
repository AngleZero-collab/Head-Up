import XCTest
@testable import HeadUp

@MainActor
final class PostureActivityServiceTests: XCTestCase {
    private let base = Date(timeIntervalSince1970: 1_800_000_000)

    private func content(_ status: PostureActivityAttributes.Status = .good,
                         safe: Int64 = 0, total: Int64 = 0,
                         at date: Date? = nil) -> PostureActivityAttributes.ContentState {
        .init(status: status, safeSeconds: safe, totalSeconds: total,
              dayStart: Calendar.current.startOfDay(for: base), updatedAt: date ?? base, language: "en")
    }

    func testInFlightUpdateFinishesBeforeEndAndQueuedOldUpdateIsDiscarded() async {
        let client = FakePostureActivityClient()
        let service = PostureActivityService(client: client, now: { self.base })
        let session = UUID()
        service.offer(sessionID: session, content: content(), canStart: true)
        await service.waitForPendingUpdates()
        client.suspendNextUpdate = true
        service.offer(sessionID: session, content: content(.attention), canStart: true, force: true)
        await client.waitUntilUpdateStarts()

        service.offer(sessionID: session, content: content(.liftHead), canStart: true, force: true)
        service.end(immediately: false)
        XCTAssertNil(service.activityID)
        XCTAssertTrue(client.ends.isEmpty, "End must wait for the in-flight update.")

        client.resumeUpdate()
        await service.waitForPendingUpdates()
        XCTAssertEqual(client.updates.map(\.status), [.attention])
        XCTAssertEqual(client.events, ["request:activity-1", "update:activity-1", "end:activity-1"])
        XCTAssertEqual(client.requests.count, 1)
        XCTAssertFalse(client.isActive("activity-1"))
    }

    func testCountersAreThrottledUntilFiveSeconds() async {
        var clock = base
        let client = FakePostureActivityClient()
        let service = PostureActivityService(client: client, now: { clock })
        let session = UUID()
        service.offer(sessionID: session, content: content(), canStart: true)
        await service.waitForPendingUpdates()
        for second in 1...4 {
            clock = base.addingTimeInterval(Double(second))
            service.offer(sessionID: session, content: content(safe: Int64(second), total: Int64(second), at: clock), canStart: true)
            await service.waitForPendingUpdates()
        }
        XCTAssertTrue(client.updates.isEmpty)
        clock = base.addingTimeInterval(5)
        service.offer(sessionID: session, content: content(safe: 5, total: 5, at: clock), canStart: true)
        await service.waitForPendingUpdates()
        XCTAssertEqual(client.updates.map(\.totalSeconds), [5])
    }

    func testNoPoseAndPauseBypassCounterThrottle() async {
        let client = FakePostureActivityClient()
        let service = PostureActivityService(client: client, now: { self.base })
        let session = UUID()
        service.offer(sessionID: session, content: content(), canStart: true)
        await service.waitForPendingUpdates()
        service.offer(sessionID: session, content: content(.noPose), canStart: false)
        await service.waitForPendingUpdates()
        service.offer(sessionID: session, content: content(.paused), canStart: false)
        await service.waitForPendingUpdates()
        XCTAssertEqual(client.updates.map(\.status), [.noPose, .paused])
    }

    func testEmptyRecordsAndGoalThresholdsUseSharedSnapshotValues() {
        let empty = content()
        XCTAssertNil(empty.goodPercent)
        XCTAssertEqual(empty.percentText, "—")
        XCTAssertEqual(empty.goalText, "No records yet")
        XCTAssertEqual(content(safe: 599, total: 599).goalText, "Building your day")

        let summary = DailyPostureSummary(date: base, safeSeconds: 480, warningSeconds: 120)
        let reached = content(safe: summary.safeSeconds, total: summary.totalTrackedSeconds)
        XCTAssertEqual(reached.goodPercent, summary.protectEyesPercent)
        XCTAssertEqual(reached.goodPercent, 80)
        XCTAssertEqual(reached.goalText, "Goal reached")
        XCTAssertEqual(content(safe: 479, total: 600).goalText, "Keep going")
    }

    func testPermissionDisabledNeverRequestsAndRequiresANewSessionAfterReenable() async {
        let client = FakePostureActivityClient()
        client.enabled = false
        let service = PostureActivityService(client: client, now: { self.base })
        let session = UUID()
        service.offer(sessionID: session, content: content(), canStart: true)
        await service.waitForPendingUpdates()
        XCTAssertTrue(client.requests.isEmpty)
        client.enabled = true
        service.offer(sessionID: session, content: content(), canStart: true, force: true)
        await service.waitForPendingUpdates()
        XCTAssertTrue(client.requests.isEmpty)
        service.offer(sessionID: UUID(), content: content(), canStart: true)
        await service.waitForPendingUpdates()
        XCTAssertEqual(client.requests.count, 1)
    }

    func testDismissalDoesNotRecreateWithinSessionButNewSessionCanStart() async {
        let client = FakePostureActivityClient()
        let service = PostureActivityService(client: client, now: { self.base })
        let session = UUID()
        service.offer(sessionID: session, content: content(), canStart: true)
        await service.waitForPendingUpdates()
        client.dismiss("activity-1")
        service.offer(sessionID: session, content: content(), canStart: true, force: true)
        await service.waitForPendingUpdates()
        service.offer(sessionID: session, content: content(.attention), canStart: true, force: true)
        await service.waitForPendingUpdates()
        XCTAssertNil(service.activityID)
        XCTAssertEqual(client.requests.count, 1)
        service.offer(sessionID: UUID(), content: content(), canStart: true)
        await service.waitForPendingUpdates()
        XCTAssertEqual(client.requests.count, 2)
        XCTAssertEqual(service.activityID, "activity-2")
    }

    func testColdInitializationImmediatelyRemovesPreviousActivities() async {
        let client = FakePostureActivityClient(existingIDs: ["previous-session", "old-summary"])
        let service = PostureActivityService(client: client)
        await service.waitForPendingUpdates()
        XCTAssertEqual(Set(client.ends.map(\.id)), ["previous-session", "old-summary"])
        XCTAssertTrue(client.ends.allSatisfy(\.immediately))
        XCTAssertTrue(client.existingIDs.isEmpty)
        XCTAssertTrue(client.requests.isEmpty)
    }

    func testResetRemovesCurrentActivityAndRetainedSummary() async {
        let client = FakePostureActivityClient()
        let service = PostureActivityService(client: client, now: { self.base })
        service.offer(sessionID: UUID(), content: content(), canStart: true)
        await service.waitForPendingUpdates()
        service.end(immediately: false)
        await service.waitForPendingUpdates()
        XCTAssertEqual(client.existingIDs, ["activity-1"])
        service.offer(sessionID: UUID(), content: content(), canStart: true)
        await service.waitForPendingUpdates()
        service.end(immediately: true)
        await service.waitForPendingUpdates()
        XCTAssertEqual(Set(client.ends.filter(\.immediately).map(\.id)), ["activity-1", "activity-2"])
        XCTAssertTrue(client.existingIDs.isEmpty)
        XCTAssertNil(service.activityID)
    }

    func testDuplicateEndDoesNotEndSameActivityTwice() async {
        let client = FakePostureActivityClient()
        let service = PostureActivityService(client: client, now: { self.base })
        service.offer(sessionID: UUID(), content: content(), canStart: true)
        await service.waitForPendingUpdates()
        service.end(immediately: false)
        service.end(immediately: false)
        await service.waitForPendingUpdates()
        XCTAssertEqual(client.ends.count, 1)
    }

    func testBackgroundOfferCannotCreateActivity() async {
        let client = FakePostureActivityClient()
        let service = PostureActivityService(client: client)
        service.offer(sessionID: UUID(), content: content(), canStart: false, force: true)
        await service.waitForPendingUpdates()
        XCTAssertTrue(client.requests.isEmpty)
    }
}

@MainActor
private final class FakePostureActivityClient: PostureActivityClient {
    struct EndCall {
        let id: String
        let immediately: Bool
    }
    var enabled = true
    private(set) var existingIDs: [String]
    private var activeIDs: Set<String>
    private(set) var requests: [PostureActivityAttributes.ContentState] = []
    private(set) var updates: [PostureActivityAttributes.ContentState] = []
    private(set) var ends: [EndCall] = []
    private(set) var events: [String] = []
    var suspendNextUpdate = false
    private var updateContinuation: CheckedContinuation<Void, Never>?
    private var startContinuation: CheckedContinuation<Void, Never>?

    init(existingIDs: [String] = []) {
        self.existingIDs = existingIDs
        activeIDs = Set(existingIDs)
    }

    func isActive(_ id: String) -> Bool { activeIDs.contains(id) }

    func request(sessionID: UUID, content: PostureActivityAttributes.ContentState) throws -> String {
        requests.append(content)
        let id = "activity-\(requests.count)"
        existingIDs.append(id)
        activeIDs.insert(id)
        events.append("request:\(id)")
        return id
    }

    func update(_ id: String, content: PostureActivityAttributes.ContentState) async {
        if suspendNextUpdate {
            suspendNextUpdate = false
            await withCheckedContinuation { continuation in
                updateContinuation = continuation
                startContinuation?.resume()
                startContinuation = nil
            }
        }
        updates.append(content)
        events.append("update:\(id)")
    }

    func end(_ id: String, content: PostureActivityAttributes.ContentState?, immediately: Bool) async {
        ends.append(.init(id: id, immediately: immediately))
        events.append("end:\(id)")
        activeIDs.remove(id)
        if immediately { existingIDs.removeAll { $0 == id } }
    }

    func dismiss(_ id: String) {
        activeIDs.remove(id)
        existingIDs.removeAll { $0 == id }
    }

    func waitUntilUpdateStarts() async {
        guard updateContinuation == nil else { return }
        await withCheckedContinuation { startContinuation = $0 }
    }

    func resumeUpdate() {
        updateContinuation?.resume()
        updateContinuation = nil
    }
}
