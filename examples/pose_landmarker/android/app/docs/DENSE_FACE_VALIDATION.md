# Dense face posture integration

Live scanning and background monitoring now run Face Landmarker VIDEO inference on the
same rotated/mirrored image used by Pose Landmarker. The callback retrieves face readings
by exact timestamp; unavailable face readings do not reuse a previous frame.

This implementation uses relative 3D landmark geometry (eye/chin axes), **not solvePnP**.
Pitch/yaw are camera-relative estimates; roll is the eye-line image angle. They are not
anatomical neck angles. Dense axes are smoothed and compared with a personal baseline.
Calibration version 6 discards older orientation baselines while retaining the original
body calibration. Recalibrate before testing. Keep the camera position/orientation stable.

The nose-to-chin projection ratio and both iris positions corroborate downward pitch.
Neither downward gaze nor an eyelid closure independently raises risk. Closed/degenerate
eye openings disable gaze evidence. Non-finite coordinates, insufficient mesh points,
tiny faces, and extreme yaw produce no dense reading. This means a severely turned or
occluded face currently uses the existing unavailable-face UI rather than a pose estimate.

Roll is displayed separately. Existing posture zones, reminders, and pet scoring consume
the fused zone. Flat-device risk is independent of calibration and is not exempted for
reclining use.

## Validation

Unit coverage includes neutral pose, roll without pitch cross-talk, image aspect ratio,
closed eyes, invalid coordinates, gaze-only negative cases, calibrated roll integration,
and uncalibrated flat-phone risk, alongside existing posture regressions.

Before claiming improved real-world accuracy, record labeled clips for:

- Upright baseline, small natural motion, upward/downward pitch and left/right yaw/roll.
- Looking down with eyes alone, blinking, glasses glare, partial occlusion and leaving view.
- Seated low-phone use, reclining and side-lying use, portrait/landscape camera changes.
- Bright/dim/backlit scenes, different faces and devices.

Measure false alarms, missed detections, reminder latency, FPS and battery use. Synthetic
unit tests do not establish an accuracy percentage. This build has no labeled-video
benchmark or calibrated camera-intrinsic solvePnP implementation.
