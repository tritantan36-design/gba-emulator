# ADR-015 — Cartridge rumble permission decision

Date: 2026-10-07. Status: Accepted — retain zero permissions.

Current Debug/Release permission baseline remains []. No permission has been added.
Pinned mRumble.setRumble receives boolean cartridge GPIO pin-3 transitions, not amplitude.

Official Android Vibrator.vibrate(VibrationEffect) requires android.permission.VIBRATE
on API 26–36. InputDevice exposes a Vibrator rather than an exemption from this permission.
View.performHapticFeedback is permission-free interaction feedback; it cannot represent
a controllable cartridge motor with explicit on/off and lifecycle cancellation. No legal,
reliable zero-permission cartridge-motor path has been identified. No hidden API,
reflection bypass, root or proprietary SDK will be used.

Sources checked 2026-10-07:
- https://developer.android.com/reference/android/os/Vibrator
- https://developer.android.com/reference/android/view/InputDevice
- https://developer.android.com/develop/ui/views/haptics/haptic-feedback
- https://developer.android.com/reference/android/Manifest.permission#VIBRATE

Option A: retain zero permissions. Implement core callback/Session/HapticOutput contract,
but real rumble is BLOCKED_BY_PERMISSION_APPROVAL and Phase 5 remains NOT READY.

Option B (recommended): user explicitly authorizes only normal permission VIBRATE,
changing the implemented Phase 4 zero-permission baseline. PROJECT_SPEC section 24 already
expressly permits VIBRATE solely for rumble; therefore no specification edit is necessary.
After approval, record it in this ADR, then add the sole manifest permission.
No dangerous runtime prompt. Debug/Release exact allowlist becomes
[android.permission.VIBRATE]; all other permissions remain forbidden.

Concrete output plan: phone vibrator only (never simultaneous gamepad output), default
user-controllable rumble switch; cartridge events polled by Session, finite pulses ≤100 ms,
≤20 dispatches/second, amplitude clamp, continuous activation ≤2 seconds then require
cartridge OFF before rearm. Cancel on pause/Home/rewind/State load/exit/ROM switch.
Unavailable actuator/system policy/call failure is surfaced as unavailable; synthetic
callbacks cannot count as physical-device acceptance. Existing battery files remain intact.

Decision: user explicitly selected “保持零权限，真实震动暂不实现” on 2026-10-07.
Manifest remains []; PROJECT_SPEC is unchanged. Real output is
BLOCKED_BY_PERMISSION_APPROVAL, not implemented or physically accepted.
Proceed with other four peripherals and synthetic rumble contracts; Phase 5 NOT READY.

## Subsequent product decision — 2026-10-07

The user explicitly confirmed that Tilt / Gyro / Solar / RTC manual experience is satisfactory,
and authorized closing Phase 5 as READY. Physical phone rumble is now classified as
**DEFERRED — V1 zero-permission policy** and does not block phase closure.
This supersedes the earlier NOT READY gate in this ADR and the Phase 5 implementation brief
for physical rumble only. The original brief and highest specification/architecture are unchanged;
the later explicit user instruction is the authority for this product scope adjustment.

Physical rumble remains unimplemented and has no physical-device PASS. Callback/mailbox and
synthetic HapticOutput evidence remain valid only for their tested contracts. No VIBRATE or
other permission is added. Existing runtime BLOCKED_BY_PERMISSION_APPROVAL text is unchanged
because this closure changes documentation only; it is not the current product-stage classification.
All historical failures, audit results, test evidence and APK hashes are preserved.
Phase 5 READY permits Phase 6 planning/implementation; this closure does not start Phase 6
or authorize a V1 release. Future physical rumble would need a separate explicit permission decision.
