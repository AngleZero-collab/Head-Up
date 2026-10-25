# HeadUp for iOS

Native iPhone and iPad app, iOS/iPadOS 17+. The product project is **HeadUp.xcodeproj**, scheme **HeadUp**. The original `PoseLandmarker.xcodeproj` and sample sources remain as reference; their Podfile is not used by HeadUp.

## Open and run

1. Open `HeadUp.xcodeproj` in Xcode on an Apple silicon Mac. Allow Swift Package Manager to resolve dependencies. The first download can be large because the official MediaPipe package includes its repository and binary frameworks.
2. Select the `HeadUp` scheme and an iPhone or iPad simulator, then Run. No signing team is required for the simulator. It supports the full UI and local storage; camera monitoring requires a physical device.
3. For a physical device, select your Apple development team under **HeadUp → Signing & Capabilities** and use a unique bundle identifier if required. Connect and trust the unlocked device, enable Developer Mode, select it and Run.
4. Tap **Start**, allow camera access and use **3-second calibration** while sitting upright about 45 cm from the screen, with your face and shoulders visible. Front and rear cameras are supported; changing lens or orientation invalidates the calibration, so calibrate again.

The default bundle identifier is `org.anglezero.HeadUp`. No credentials or signing team are committed. English and Traditional Chinese are available in Settings.

## Behavior and data

- Monitoring continues across Scan, Stats, Challenge and Shop. It pauses when HeadUp leaves the foreground, the screen locks, or the camera is interrupted. Returning to the app resumes a previously started session. Pause explicitly to stop it.
- No cross-app overlay, hidden background camera or background monitoring is implemented. Camera images stay on device and are not recorded or uploaded.
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

The project is checked in and needs no generator to open. After adding/removing source files, regenerate it with `python3 RunScripts/generate_headup_project.py` (Python standard library only). The icon can be regenerated with `swift RunScripts/generate_icon.swift HeadUp/Assets.xcassets/AppIcon.appiconset/Icon.png`.

## Physical-device acceptance remaining

Simulator success does not validate camera geometry, haptics, thermal behavior or power use. On both an iPhone and iPad: verify portrait/landscape preview alignment, calibration, front/rear switching, permission denial/recovery, calls/camera interruptions, background/lock/resume, and a 30-minute session. Confirm no extra time is awarded during interruption and document heat/battery observations. Device signing, installation and those physical checks require the owner's devices and team.

See `VALIDATION.md` for results produced on the development machine. TestFlight and App Store distribution are outside this delivery.

## Source and licenses

This port derives its rules and assets from the repository's Android HeadUp implementation and the existing MediaPipe iOS sample. Preserve the repository's Apache 2.0 license and upstream notices. MediaPipe setup: https://developers.google.com/edge/mediapipe/solutions/setup_ios
