# Heads Up for iOS

Native iPhone and iPad app, iOS/iPadOS 17+. The product project is **HeadUp.xcodeproj**, scheme **HeadUp**. The original `PoseLandmarker.xcodeproj` and sample sources remain as reference; their Podfile is not used by Heads Up.

## Open and run

1. Open `HeadUp.xcodeproj` in Xcode on an Apple silicon Mac. Allow Swift Package Manager to resolve dependencies. The first download can be large because the official MediaPipe package includes its repository and binary frameworks.
2. Select the `HeadUp` scheme and an iPhone or iPad simulator, then Run. No signing team is required for the simulator. It supports the full UI and local storage; camera monitoring requires a physical device.
3. For a physical device, select your Apple development team under **HeadUp → Signing & Capabilities** and use a unique bundle identifier if required. Connect and trust the unlocked device, enable Developer Mode, select it and Run.
4. Tap **Start**, allow camera access and use **3-second calibration** while sitting upright about 45 cm from the screen, with your face and shoulders visible. Front and rear cameras are supported; changing lens or orientation invalidates the calibration, so calibrate again.

The app display name is **Heads Up** in both languages. Internal Xcode project/scheme names and the bundle identifier remain stable so existing installations and local data continue to work. The default bundle identifier is `org.anglezero.HeadUp`. No credentials or signing team are committed. English and Traditional Chinese are available in Settings.

## Small camera window / 小視窗

For the iPhone feasibility build, select **HeadUp PiP Preview** in Xcode and run on a physical **iPhone with iOS 18 or later**. Your local signing team is retained when regenerating the project.

1. Start monitoring and complete calibration on the Scan tab.
2. Scroll to **Monitor while using other apps / 使用其他 App 時繼續偵測** and tap **Open small window / 開啟小視窗**.
3. Wait for the small window to appear, then open another app. Keep your face and shoulders in camera view. Posture analysis and valid monitoring time continue only while real frames arrive.
4. Use the system window's return control to return to Scan. Closing the window while Heads Up is in the background stops monitoring. Closing it while Heads Up is foreground continues ordinary monitoring. Locking or hiding the window may interrupt the camera; return to Heads Up to restart.

The **PiPPreview** configuration declares `audio` and `voip` background modes because Apple's iOS 18 multitasking-camera support on iPhone depends on the VoIP declaration. This is a **local feasibility configuration**, not an assertion that a posture-only app qualifies as a calling app. It does not simulate a call, use CallKit/PushKit, record audio, or contact a server. AVKit uses a playback audio session that mixes with other apps; no silent audio is played. The normal **HeadUp** Debug/Release configuration declares only `audio`, checks actual camera support, and may refuse PiP on iPhone. Both schemes archive using **Release**, without the experimental `voip` declaration. TestFlight/App Store distribution requires resolving the permitted use of this background mode with Apple first.

The simulator cannot validate camera capture or background inference. Runtime support checks and error messages handle unsupported devices/builds. Camera access may be interrupted by another camera app, a hidden PiP window, locking, or system pressure. Background sound/haptics follow system policy and are not guaranteed; the small window supplies the visible posture warning.

References: [Apple PiP camera setup](https://developer.apple.com/documentation/avkit/adopting-picture-in-picture-for-video-calls), [multitasking camera support conditions](https://developer.apple.com/documentation/avfoundation/avcapturesession/ismultitaskingcameraaccesssupported), [background service purposes, 2.5.4](https://developer.apple.com/app-store/review/guidelines/#software-requirements).

## Little Blue in Dynamic Island and on the Lock Screen

A Live Activity starts after the camera starts successfully, when **Settings → Dynamic Island and Lock Screen / 靈動島與鎖定畫面** is enabled (default). The existing PiP camera window is still needed for cross-app camera monitoring. The Live Activity itself grants no camera or background execution permission.

- Compact: dragon, current posture text and status icon. Long press to expand: current status, today's good-posture share and effective monitored minutes. A sustained two-second danger alert or the existing rapid-fall condition shows **Look up / 抬頭**; before that, warning/danger posture shows **Careful / 注意**.
- **View today / 查看今日** opens Stats. **Pause / 暫停偵測** stops the existing camera session and PiP without opening the app; an old activity cannot stop a newer session. The Lock Screen keeps the final summary for 15 minutes. Tap the remaining activity area to open Scan.
- No pose, calibration, interrupted camera and paused monitoring have explicit non-good states. Active snapshots expire after ten seconds without updates; ActivityKit controls actual rendering time. Updates are coalesced, normally every five seconds, with earlier state-change updates. Durations are measured values, never a timer that keeps counting while detection is unavailable.
- Today uses exactly the same totals as Stats: no records means “—”; under ten minutes means “Building your day / 累積中”; after that the existing 80% goal determines the goal label. The record date is included so old summaries are not mistaken for new-day data.
- Closing a Live Activity does not stop camera monitoring, and Heads Up does not recreate it within the same session. Explicitly pause/start monitoring to begin another. Disabling the setting or resetting local data removes activities and retained summaries. Cold app initialization clears lingering activities. System expiry/authorization limits are respected.

The widget extension contains only SwiftUI, ActivityKit/App Intents and small still images extracted from the original dragon movies. It does not load MediaPipe, read the posture database, record images or contact a server. There are no push notifications, extra alert sounds or looping widget videos. Devices without Dynamic Island use the Lock Screen presentation. The camera/PiP compatibility restrictions above still apply.

Widget layouts have Xcode previews in `HeadUpWidgets/HeadUpLiveActivity.swift`. Re-extract the original stills with `swift RunScripts/extract_dragon_stills.swift` from this directory. Shared activity/intent types are compiled into the app and extension by the project generator; the extension receives its signing team from the locally configured app target.

## Behavior and data

- Monitoring continues across Scan, Stats, Challenge and Shop. With an explicitly opened, supported Picture in Picture camera window, it also processes frames while another app is foreground. Otherwise leaving Heads Up pauses monitoring. Locking, closing the background window, camera interruption or missing poses breaks time continuity; interrupted time earns no progress.
- The small window shows a live camera preview, skeleton, posture status and the existing danger border. Camera images stay on device and are not recorded or uploaded. There is no hidden camera mode, fake call, microphone capture, or silent audio loop.
- Three seconds of valid calibration samples establish the baseline. Missing/low-confidence poses interrupt an in-progress calibration and do not count as good posture.
- The analyzer preserves the Kotlin face/shoulder geometry, 0.15 EMA, angle/distance/shoulder thresholds and per-frame rapid-fall heuristic. A sustained danger state triggers an in-app border after **2 seconds**, matching the Android code rather than the root README's 3-second description. This is not a prediction of posture two seconds into the future.
- Safety corrections: missing poses clear old metrics and smoothing; interrupted intervals earn no time or rewards; rapid-fall feedback is triggered on the rising edge. Haptic cooldown is 1.5 seconds; optional warning sound cooldown is 3 seconds. Device mute settings and haptic availability apply.
- SwiftData stores measurements for 90 days and local calibration, daily progress, points and owned rewards. No accounts, cloud synchronization, Android history import, real-money purchases or cosmetic equipment effects are included.
- Tasks retain Android rules: 30 good-posture minutes → 50 points; three confirmed eye breaks → 50; at least 10 monitored minutes with 80% good posture → 100. Daily rewards can be claimed once. Shop costs are 300/180/500/220 for the original four items; ownership is permanent until reset.
- Settings reset clears history, calibration, progress, points, owned items and preferences. The dashboard's streak retains the original seven-day window.

## Implementation

- `HeadUpCore`: pure Swift models, posture analysis and alert timing, independently tested with Android fixtures.
- `HeadUp/Camera`: serial AVFoundation capture and MediaPipe inference; session generation tokens reject stale callbacks. Camera buffers are rotated and mirrored before inference, and the preview uses matching transforms. Frame processing uses video mode on a serial queue, discarding late frames to bound work.
- `HeadUp/Persistence`: SwiftData profile and measurement models. Exact valid elapsed milliseconds feed both dashboard totals and rewards. Reward claims and purchases save atomically with rollback on failure; storage initialization errors are shown rather than deleting data.
- `HeadUp/UI`: SwiftUI, Swift Charts and existing dragon videos. Resource assets were copied into iOS; Android files are unchanged.

MediaPipeTasksVision **1.1.1**, official commit `50505c8312c7eacb1bd411cecdc4cc9fd08d53cb`, is pinned in the project and `Package.resolved`. The bundled Full model uses CPU, one pose, and detection/presence/tracking confidence 0.5. This release's simulator binaries require **arm64**; Intel simulator builds are not supported. UIKit, Metal and OpenGLES are explicitly linked for the official static package. MediaPipe is linked once in the app host to avoid duplicate graph registration in tests.

## Build and tests

```sh
# From this ios directory:
swift test --package-path HeadUpCore
xcodebuild -project HeadUp.xcodeproj -scheme HeadUp \
  -destination 'platform=iOS Simulator,name=iPhone 17' test
xcodebuild -project HeadUp.xcodeproj -scheme HeadUp \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

Use an installed simulator name from `xcrun simctl list devices available`. The app-hosted smoke test loads the real Full model and detects 33 landmarks in the bundled reference image. Store tests cover exact elapsed time, gaps, danger episodes, daily rewards, purchases, resets, midnight and disk relaunch. UI tests cover tab navigation, settings, language and unavailable camera behavior.

The project is checked in and needs no generator to open. After adding/removing source files, regenerate it with `python3 RunScripts/generate_headup_project.py` (Python standard library only). Signing choices already saved in Xcode are preserved locally by the generator. The icon can be regenerated with `swift RunScripts/generate_icon.swift HeadUp/Assets.xcassets/AppIcon.appiconset/Icon.png`.

## Physical-device acceptance remaining

Simulator success does not validate camera geometry, haptics, thermal behavior or power use. On both an iPhone and iPad: verify portrait/landscape preview alignment, calibration, front/rear switching, permission denial/recovery, calls/camera interruptions, background/lock/resume, and a 30-minute session. Confirm no extra time is awarded during interruption and document heat/battery observations. Device signing, installation and those physical checks require the owner's devices and team.

See `VALIDATION.md` for results produced on the development machine. TestFlight and App Store distribution are outside this delivery.

## Source and licenses

This port derives its rules and assets from the repository's Android HeadUp implementation and the existing MediaPipe iOS sample. Preserve the repository's Apache 2.0 license and upstream notices. MediaPipe setup: https://developers.google.com/edge/mediapipe/solutions/setup_ios
