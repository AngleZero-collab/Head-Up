# iOS validation

Environment: Apple silicon Mac, Xcode 27.0 (27A266a), Swift 6.4 compiler in Swift 5 language mode, iOS 27.0 simulator. Deployment target: iOS/iPadOS 17. Tests on older supported OS versions remain a device-lab follow-up.

Base commit: `70bf9848ed98bc8f2cc7dbd3248d2a00ac036681`. Delivery branch: `codex/ios-port`.

| Check | Result |
| --- | --- |
| Official MediaPipe 1.1.1 dependency resolution | Passed, pinned revision |
| Pure Swift HeadUpCore tests | 36 passed, including the original 29 and 7 PiP lifecycle tests |
| SwiftData tests on iPhone simulator | 6 passed: timing/gaps, fractions, danger episodes, rewards/purchases/reset, midnight, disk relaunch |
| Real MediaPipe model smoke test | Passed: Full model detected 33 finite landmarks in the bundled image |
| Four-tab navigation and settings UI | Passed on iPhone 17 simulator |
| Traditional Chinese language and reset confirmation UI | Passed on iPhone 17 simulator |
| Simulator camera-unavailable UI and empty statistics | Passed on iPhone 17 simulator; final targeted XCTest command succeeded |
| iPad launch and layout inspection | Passed: iPad mini (A17 Pro) simulator, Traditional Chinese, side-by-side scan/results layout |
| Release build for generic iOS device, signing disabled | Passed |
| Android and all non-iOS paths | No changes relative to base |
| Physical iPhone installation | PiP Preview installed and signed on iPhone 15 Pro, iOS 26.6.1 |
| Physical iPad and 30-minute session | Not performed |

The project explicitly links UIKit, Metal and OpenGLES required by the official static MediaPipe package. Its shipped simulator framework supports arm64; this project does not claim Intel simulator support. Xcode emits a SwiftData predicate Sendable diagnostic under targeted concurrency in Swift 5 mode and an informational App Intents extraction warning; neither blocks builds or tests.

Real camera orientation/mirroring, device interruptions, haptic behavior and heat/battery measurements still require physical iPhone and iPad testing. No synthetic pose stream is presented as real monitoring in the simulator. The original sample test image is used only by the explicit inference smoke test.

## Picture in Picture update — 2026-10-26

- PiP Preview simulator build passed on Apple silicon. Two app-hosted tests verify that unsupported cameras/missing source windows cannot enter a background handoff. The simulator UI test confirms the small-window control requires active monitoring and explains physical-device testing.
- Seven new core tests cover foreground, inactive/background without PiP, PiP startup/active handoff, failure/closure, return to foreground, lock and unlock. All 36 core tests passed.
- **Physical iPhone 15 Pro, iOS 26.6.1:** signed PiP Preview build installed. An automated device test started the actual camera and PiP window, brought Safari to the foreground for eight seconds, returned to HeadUp, and verified more than three real MediaPipe inference callbacks were processed while the app's scene was background and PiP was active. After explicit pause, a second background interval produced no additional callbacks. This is real camera inference, not a synthetic stream. It does not assert that a person was in frame or establish posture-estimation accuracy.
- Test launches use isolated in-memory data; no captured video, camera image, Safari content, account identifier or signing team is committed. The numeric background frame counter exists only in the preview's opt-in device test UI.
- Continuous 30-minute monitoring, background warning audibility/haptics, physical iPad PiP, low-power/thermal behavior, rotation after PiP restoration, system close/hide/lock, another app taking the camera and older OS versions remain manual checks. The normal Release configuration excludes `voip`; no App Store eligibility is claimed.

To repeat the real-device smoke test, select **HeadUp PiP Preview**, configure the signing team for app and test targets, and run `HeadUpUITests/testDevicePiPReceivesFramesWhileUsingAnotherApp` on a trusted physical iPhone. The test briefly opens Safari without navigating or interacting with its page, then returns to HeadUp. It skips on simulators and non-preview configurations.

## Dynamic Island implementation — 2026-10-30

The user requested implementation only and stopped further local/device validation. No further tests were started after that instruction.

- Implemented the WidgetKit Live Activity extension, shared attributes and pause intent, serialized/coalesced ActivityKit service, existing-session pause routing, Stats deep link, persisted opt-out with backward-compatible profile decoding, and Little Blue still images extracted from the original videos.
- Before the stop request, the iPhone simulator run completed with **22 app-hosted tests passing**: the existing nine plus thirteen activity service/integration tests. These cover update ordering, throttle, dismissal, denied authorization, cold cleanup, reset/duplicate end, two-second/rapid-fall mapping, stale/no-pose states, zero records, midnight, and preference migration.
- The PiP Preview app and embedded widget extension built for simulator and signed device targets. The physical device UI attempt **did not pass**: after starting PiP, automation could not locate the expanded activity's `activityViewToday` control. It did not establish that the view-today or pause actions work on-device. The cause was not resolved before the user stopped testing.
- Final cleanup retains handles to ended activities for immediate removal on reset/opt-out; this final change has not been rerun through tests. Release configuration, compact/minimal/expanded/Lock Screen layout, direct intent interaction, permission denial, system activity competition, localization/accessibility, lock/resume and physical iPad behavior remain unverified for this feature.
- Existing result screenshots/logs remain outside the repository. No device camera imagery or local signing information is included in the commit.

When validation resumes, run `HeadUpTests/PostureActivityServiceTests`, `HeadUpTests/PostureActivityIntegrationTests`, `HeadUpUITests/testLiveActivityPreferenceAndTodayLink`, and the opt-in device test `HeadUpUITests/testDeviceDynamicIslandTodayAndPause`. The device test requires an unlocked iPhone with Dynamic Island, Live Activities enabled for HeadUp, and an unobstructed system activity surface. Its coordinate-based expansion step may need adjustment to the current system UI; do not interpret it as a successful feature check until the assertions pass.

## Repeat validation

Run the commands in `README.md`. All tests use isolated in-memory data or a temporary disk database. UI tests launch with `--uitesting`; normal app launches use persistent SwiftData storage. The standalone core package can be tested without an iOS simulator or MediaPipe downloads.

Before committing, verify scope from the repository root:

```sh
git diff --cached --check
git diff --cached --name-only
git diff 70bf9848ed98bc8f2cc7dbd3248d2a00ac036681 -- examples/pose_landmarker/android
```

All changed paths must start with `examples/pose_landmarker/ios/`, and the Android diff must be empty. No merge into `main` is part of this delivery.
