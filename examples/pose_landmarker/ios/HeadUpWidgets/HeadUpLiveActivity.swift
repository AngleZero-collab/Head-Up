import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

@main
struct HeadUpWidgets: WidgetBundle {
    var body: some Widget { HeadUpLiveActivity() }
}

struct HeadUpLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PostureActivityAttributes.self) { context in
            PostureActivityCard(context: context)
                .padding(14)
                .activityBackgroundTint(Color(red: 7 / 255, green: 20 / 255, blue: 38 / 255))
                .activitySystemActionForegroundColor(.white)
                .widgetURL(URL(string: "headup://scan"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    ActivityDragon(state: context.state, stale: context.isStale, size: 48)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(context.state.dayLabel()).font(.caption).foregroundStyle(.secondary)
                        Text(context.state.percentText).font(.title3.bold()).monospacedDigit()
                        Text(context.state.text("Good posture", "正確姿勢")).font(.caption2)
                    }
                    .accessibilityElement(children: .combine)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(context.state.text("Vision Dragon", "視力龍")).font(.caption).foregroundStyle(.secondary)
                        PostureStatus(state: context.state, stale: context.isStale)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 8) {
                        HStack {
                            Label(context.state.trackedDuration, systemImage: "clock")
                            Spacer()
                            Text(context.state.goalText)
                        }
                        .font(.caption).foregroundStyle(.secondary)
                        ActivityActions(context: context)
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                ActivityDragon(state: context.state, stale: context.isStale, size: 24)
            } compactTrailing: {
                Label(context.state.compactTitle(isStale: context.isStale),
                      systemImage: context.state.isOutdated(context.isStale) ? "clock.badge.exclamationmark" : context.state.status.symbol)
                    .font(.caption2.bold())
                    .foregroundStyle(activityColor(context.state, stale: context.isStale))
                    .lineLimit(1).minimumScaleFactor(0.8)
                    .accessibilityLabel(context.state.statusTitle(isStale: context.isStale))
            } minimal: {
                ActivityDragon(state: context.state, stale: context.isStale, size: 22)
            }
            .widgetURL(URL(string: "headup://scan"))
            .keylineTint(activityColor(context.state, stale: context.isStale))
        }
    }
}

private func activityColor(_ state: PostureActivityAttributes.ContentState, stale: Bool) -> Color {
    guard !state.isOutdated(stale) else { return .gray }
    return state.status.dragonTint
}

private struct ActivityDragon: View {
    let state: PostureActivityAttributes.ContentState
    let stale: Bool
    let size: CGFloat

    var body: some View {
        PostureDragonView(status: state.status, isStale: stale, size: size)
            .accessibilityHidden(false)
            .accessibilityLabel("\(state.text("Vision Dragon", "視力龍")) · \(state.statusTitle(isStale: stale))")
    }
}

private struct PostureStatus: View {
    let state: PostureActivityAttributes.ContentState
    let stale: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Label(state.statusTitle(isStale: stale), systemImage: state.isOutdated(stale) ? "clock.badge.exclamationmark" : state.status.symbol)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(activityColor(state, stale: stale))
            .fixedSize(horizontal: false, vertical: true)
            .transaction { if reduceMotion { $0.animation = nil } }
    }
}

private struct PostureActivityCard: View {
    let context: ActivityViewContext<PostureActivityAttributes>

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 12) {
                ActivityDragon(state: context.state, stale: context.isStale, size: 48)
                VStack(alignment: .leading, spacing: 3) {
                    Text(context.state.text("Heads Up · Vision Dragon", "Heads Up · 視力龍")).font(.caption).foregroundStyle(.secondary)
                    PostureStatus(state: context.state, stale: context.isStale)
                    Text("\(context.state.dayLabel()) · \(context.state.goalText)")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 3) {
                    Text(context.state.percentText).font(.title2.bold()).monospacedDigit()
                    Text(context.state.text("Good posture", "正確姿勢")).font(.caption2)
                    Text(context.state.trackedDuration).font(.caption).foregroundStyle(.secondary)
                }
                .accessibilityElement(children: .combine)
            }
            ActivityActions(context: context)
        }
        .foregroundStyle(.white)
    }
}

private struct ActivityActions: View {
    let context: ActivityViewContext<PostureActivityAttributes>

    var body: some View {
        HStack(spacing: 12) {
            Link(destination: URL(string: "headup://today")!) {
                Label(context.state.text("View today", "查看今日"), systemImage: "chart.bar")
                    .frame(maxWidth: .infinity, minHeight: 32)
            }
            .accessibilityIdentifier("activityViewToday")
            if context.state.status != .ended {
                Button(intent: PauseMonitoringIntent(sessionID: context.attributes.sessionID.uuidString)) {
                    Label(context.state.text("Pause", "暫停偵測"), systemImage: "pause.fill")
                        .frame(maxWidth: .infinity, minHeight: 32)
                }
                .accessibilityIdentifier("activityPauseMonitoring")
            }
        }
        .font(.caption.weight(.semibold))
        .buttonStyle(.bordered)
        .tint(Color(red: 56 / 255, green: 200 / 255, blue: 244 / 255))
    }
}

#Preview("Good · English", as: .dynamicIsland(.expanded), using: PostureActivityAttributes(sessionID: UUID())) {
    HeadUpLiveActivity()
} contentStates: {
    PostureActivityAttributes.ContentState(status: .good, safeSeconds: 900, totalSeconds: 1000,
                                          dayStart: Calendar.current.startOfDay(for: Date()), updatedAt: Date(), language: "en")
}

#Preview("抬頭 · 鎖定畫面", as: .content, using: PostureActivityAttributes(sessionID: UUID())) {
    HeadUpLiveActivity()
} contentStates: {
    PostureActivityAttributes.ContentState(status: .liftHead, safeSeconds: 400, totalSeconds: 1000,
                                          dayStart: Calendar.current.startOfDay(for: Date()), updatedAt: Date(), language: "zh-Hant")
}
