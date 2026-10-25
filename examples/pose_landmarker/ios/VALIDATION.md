# iOS validation

Environment: Apple silicon Mac, Xcode 27.0 (27A266a), Swift 6.4 compiler in Swift 5 language mode, iOS 27.0 simulator. Deployment target: iOS/iPadOS 17. Tests on older supported OS versions remain a device-lab follow-up.

Base commit: `70bf9848ed98bc8f2cc7dbd3248d2a00ac036681`. Delivery branch: `codex/ios-port`.

| Check | Result |
| --- | --- |
| Official MediaPipe 1.1.1 dependency resolution | Passed, pinned revision |
| Pure Swift HeadUpCore tests | 29 passed, including all original Android numeric fixtures |
| SwiftData tests on iPhone simulator | 6 passed: timing/gaps, fractions, danger episodes, rewards/purchases/reset, midnight, disk relaunch |
| Real MediaPipe model smoke test | Passed: Full model detected 33 finite landmarks in the bundled image |
| Four-tab navigation and settings UI | Passed on iPhone 17 simulator |
| Traditional Chinese language and reset confirmation UI | Passed on iPhone 17 simulator |
| Simulator camera-unavailable UI and empty statistics | Passed on iPhone 17 simulator; final targeted XCTest command succeeded |
| iPad launch and layout inspection | Passed: iPad mini (A17 Pro) simulator, Traditional Chinese, side-by-side scan/results layout |
| Release build for generic iOS device, signing disabled | Passed |
| Android and all non-iOS paths | No changes relative to base |
| Physical iPhone/iPad installation and 30-minute session | Not performed; user selected simulator validation |

The project explicitly links UIKit, Metal and OpenGLES required by the official static MediaPipe package. Its shipped simulator framework supports arm64; this project does not claim Intel simulator support. Xcode emits a SwiftData predicate Sendable diagnostic under targeted concurrency in Swift 5 mode and an informational App Intents extraction warning; neither blocks builds or tests.

Real camera orientation/mirroring, device interruptions, haptic behavior and heat/battery measurements still require physical iPhone and iPad testing. No synthetic pose stream is presented as real monitoring in the simulator. The original sample test image is used only by the explicit inference smoke test.

## Repeat validation

Run the commands in `README.md`. All tests use isolated in-memory data or a temporary disk database. UI tests launch with `--uitesting`; normal app launches use persistent SwiftData storage. The standalone core package can be tested without an iOS simulator or MediaPipe downloads.

Before committing, verify scope from the repository root:

```sh
git diff --cached --check
git diff --cached --name-only
git diff 70bf9848ed98bc8f2cc7dbd3248d2a00ac036681 -- examples/pose_landmarker/android
```

All changed paths must start with `examples/pose_landmarker/ios/`, and the Android diff must be empty. No merge into `main` is part of this delivery.
