# ADR-014 — GBA peripheral ownership and mapping

Accepted before implementation, 2026-10-07. Phase 5 only; Phase 6 not started.

Keep mGBA 0.10.5 commit 26b7884bc25a5933960f3cdcd98bac1ae14d42e2,
Oboe and toolchain unchanged. Android application SensorAdapter owns application-context
listeners; pure input/SensorMapper owns rotations/filter/calibration. Immutable scalar
samples flow through Session/Core API/Adapter/JNI. Native callbacks and hardware flags
are accessed under the existing core control mutex, never from an audio callback.
Samples are latest-only, not an event backlog. A frame-latched snapshot keeps X/Y coherent.

RTC retains mCore's RTC_NO_OVERRIDE system epoch clock and native localtime conversion.
Pacing uses monotonic time separately. Raw official state format remains version 7 and
preserves the existing RTC serialized transaction fields; no custom clock or save migration.

Use gravity in m/s², accelerometer low-pass fallback, display-relative X/Y, neutral
calibration, finite bounds and dead zone. Tilt callback scale follows pinned Libretro
(-2e8 X, +2e8 Y). Gyro is rad/s about screen normal, separately calibrated, scaled -5.5e8,
bounded before conversion. Solar uses logarithmic lux→0..10, then pinned GBA_LUX_LEVELS
and inverted byte (255 - (22 + level)); manual 0..100% maps monotonically to the same levels.

Detect capabilities from GBA.memory.hw.devices after ROM load/reset, not filename.
Manual override uses pinned hardware initializers under control mutex without changing
save type, ROM bytes or core identity. AUTO samples only detected devices. Explicit
manual modes can mount undetected hardware; DISABLED supplies neutral/dark values.
Persist global settings in a separate version-1 bounded DataStore file; keep display
v1/v2 files, GameId, Room, battery and State metadata schema unchanged.

Native rumble callback updates a bounded latest-event mailbox (including stop epoch).
Session drains on a controlled application ticker; HapticOutput never runs on core/audio
threads. Phone is the sole selected actuator, with bounded pulses, rate limit and maximum
continuous activation. Pause/background/rewind/load/close/switch reset samples and stop
output. Resume requires new physical samples. These actuator limits describe the proposed
physical-output design, not implemented behavior. Subsequent decision (2026-10-07):
ADR-015 records the user's zero-permission choice; physical output is disabled and
BLOCKED_BY_PERMISSION_APPROVAL. Only callback/mailbox and synthetic contracts are implemented.

Use original Apache-2.0 GPIO/tilt probe ROMs and explicit mapper configuration, documenting
which tests are true ROM reads versus synthetic adapter contracts. No ROM in main APK.

Test-only amendment: this phone froze the standalone JNI process while the screen was
locked (do_freezer_trap). A visible androidTest-only Activity/runner holds KEEP_SCREEN_ON
during JNI tests and finishes afterward. No WAKE_LOCK, service, production Activity,
permission or native test assertion changes. Re-run unchanged JNI tests with the harness.
