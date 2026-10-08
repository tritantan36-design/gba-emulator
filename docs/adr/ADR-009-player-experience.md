# ADR-009 — Player experience controls and ownership

Date: 2026-10-06. Status: Accepted for Phase 3 only.

Preserve the fixed toolchain, upstream sources and Phase 2 persistence format.

## Decisions

- The native per-session worker owns pacing, rewind snapshots and framebuffer publication. Core API exposes bounded control methods and metrics, without native types. UI calls Session only.
- FF uses a monotonic frame deadline derived from mGBA frameCycles/frequency divided by 1/2/4/8, with bounded deadline catch-up. FF and rewind stop Oboe and drain/discard generated PCM. Returning to normal opens one stream with an empty queue.
- Rewind captures raw mGBA states plus the framebuffer every 12 emulated frames (~201 ms), up to 150 snapshots and 64 MiB including frame bytes. Reverse playback pops snapshots at 50 ms; future history is discarded. No rewind files or storage calls. Preserve current cloned battery around every loadState, including NoSave. Manual State loads reset rewind history. Low-memory clears only this cache.
- Native controls and ownership use the existing control mutex/worker; memory allocation failure clears rewind and continues normal play. No global core. Pause cancels FF/rewind, keys and audio before persistence.
- Session tracks user pause separately from foreground. Pause menu, editor and lifecycle cannot accidentally resume a user-paused game. Rotation retains the ViewModel/core; input is released and Surface rebuilt.
- InputRouter tracks button sets by source/pointer/device, merging touch, keyboard and HID. All cancellation/disconnection routes release sources. D-pad uses normalized hit testing and radius-dependent dead zone.
- Separate normalized portrait/landscape profiles, atomic private JSON persistence (Android built-in JSON/AtomicFile), default mapping and per-device InputDevice key/axis handling. Android owns Bluetooth pairing; no permissions.
- One Menu entry during gameplay. Menu opens a paused dialog; an optional modal hold control resumes play for FF/rewind and closes on release. No persistent extra floating controls.
- A one-off framebuffer copy becomes WebP only for explicit screenshots/State thumbnails, never per frame. Private screenshots and per-game thumbnails are replaceable independent files; a sidecar capture timestamp associates thumbnails with State metadata. State commit remains authoritative; thumbnail errors never fail State saves. UI hides stale thumbnails whose timestamp differs.
- No new libraries or permissions. Atomic profile JSON uses an app-owned store. No shader/sensor/library changes.

Low-memory trims lower this core's rewind payload limit to 16 MiB until a fresh core, rather than
immediately refilling the 64 MiB cache. Rewind-active periodic checkpoints defer; background/close
flush current battery and skip autosave State capture for that rewind-active checkpoint.
Default touch hit areas stay at least 48 dp, inside system safe insets plus an additional side margin.

## Validation and limits

Run Phase 2 regression, native ownership/pacing/rewind, source-aware input, screenshots, rotation and JNI UBSan. Actual throughput is measured rather than assumed from requested multiplier. Physical USB/Bluetooth and comfort checks remain separately reported from synthetic mapping tests. ASan and long-play limitations are retained when not measured.
