# Android test device strategy

Phase 7A-0 is test infrastructure, not a product feature phase. Frozen build tools,
core commits, permissions, Game ID and persistence formats remain unchanged.

Primary automated target: **API 34 Emulator** (actual infrastructure PASS_WITH_NOTE
in PHASE_7A_0_ANDROID_EMULATOR_INFRASTRUCTURE_REPORT.md).
Compatibility targets: API 26, API 29 and API 36, one AVD at a time. Existing API31
evidence and isolated historical-data AVD are retained, not discarded or relabelled.

Use official Android SDK images only. Query actual guest ABI and native bridge
before installing an APK; do not modify Android ABI properties or install unknown
translation binaries. ARM translation is not a native-phone performance baseline.
Record graphics backend, host GPU, image revision and emulator version for failures.
Prefer auto/hardware graphics; use software only with recorded compatibility reason.

## Reusable commands

Set ANDROID_HOME (or ANDROID_SDK_ROOT); otherwise scripts read local.properties.
Set ANDROID_AVD_HOME to the existing project AVD directory if non-default.
Use `python scripts/android_emulator.py --help` for available commands. Every
device command requires `--serial emulator-NNNN` and verifies the project AVD name.

```powershell
python scripts/android_emulator.py detect
python scripts/android_emulator.py start --serial emulator-5570 --avd GBA_Lite_API34 --graphics host --output docs/reports/evidence/emulator/new-api34-boot.txt
python scripts/android_emulator.py wait --serial emulator-5570
python scripts/android_emulator.py install --serial emulator-5570 --apk <debug-apk>
python scripts/android_emulator.py launch --serial emulator-5570
python scripts/android_emulator.py home --serial emulator-5570
python scripts/android_emulator.py launch --serial emulator-5570
python scripts/android_emulator.py rotate --serial emulator-5570 --rotation 1
python scripts/android_emulator.py screenshot --serial emulator-5570 --output docs/reports/evidence/emulator/landscape.png
python scripts/android_emulator.py logcat --serial emulator-5570 --output docs/reports/evidence/emulator/logcat.txt
python scripts/android_emulator.py logcat --serial emulator-5570 --filtered --output docs/reports/evidence/emulator/app-logcat.txt
python scripts/android_emulator.py relaunch --serial emulator-5570
python scripts/android_emulator.py stop --serial emulator-5570
```

Explicit `clear`, `uninstall` and `shutdown` actions are available only for owned
AVDs. They must never select a physical phone. Save evidence before resetting a QA
AVD; do not clear historical-upgrade/state fixture AVDs. `stop` is a controlled
process termination, not proof of spontaneous crash recovery.

For offscreen menu items use `scroll --direction down` before `tap --text ...`.
Scroll uses the unique live scroll-container bounds; tap requires a unique visible
node. A failed lookup is a failed automation step, not a successful click. In
PowerShell batch scripts check native exit codes (or enable
`$PSNativeCommandUseErrorActionPreference=$true` with Stop error handling).

`start` uses a hidden headless process and refuses to overwrite an existing boot log.
Set ANDROID_AVD_HOME before starting/listing a non-default AVD. On this host the
verified hardware backend is `host`; initial `auto` selected SwiftShader. Start one
AVD at a time, save results and shut it down when finished. Fresh Android images
may show a standard fullscreen education prompt; inspect and dismiss its actual
"Got it" UI before tests that assume menu controls are unobstructed. Do not disable
system security policies or modify APK behavior to suppress prompts.

Build only required Debug / androidTest targets. Reuse existing valid JVM, host,
APK audit, reproducibility, migration and RTC evidence. New infrastructure smoke
must actually install/launch/test/capture; it does not require repeating all Phase7
tests. Full 50–100-cycle stress belongs to 7A. Long-run must be an explicit opt-in.
The stopped phone long-run will not restart automatically.

## Emulator scope

Automate install/launch/relaunch, real SAF open/import/cancel, legal internal ROMs,
save/load, Room/DataStore migrations, orientation, Home/foreground, library stress,
renderer lifecycle, audio start/stop counters, sensor registration and RTC logic.
Only self-authored/redistributable ROM fixtures and synthetic records enter scripts,
test assets or CI. Commercial ROMs never enter the repository or automation.
Keep logs/screenshots in `docs/reports/evidence/emulator/`, with unique run names
when repeating a failed run. Preserve failures and skipped tests separately.

## Physical device required

| Area | Required evidence |
|---|---|
| HID | Actual Bluetooth / USB controller |
| Sensors | Actual Tilt / Gyro / Solar behavior (or clearly labelled controlled injection) |
| OEM | Manufacturer lifecycle, lock screen / Doze behavior |
| Graphics | Real GPU driver and final renderer validation |
| Audio | Latency, audible artifacts, speaker / Bluetooth / USB routing |
| Resources | Temperature, battery and sustained physical-device load |
| UX | Final touch comfort and 30–60 minute legitimate gameplay |

Emulator results never replace explicitly required physical-device acceptance.
Prior user feedback is retained as historical acceptance, not new Phase7 results.
No phone action is needed while suitable automatic tests run in the emulator.

Gradle Managed Devices and remote emulator CI are evaluated after the ordinary
API34 AVD works. Neither requires upgrading frozen tools. Configuration alone is
not a remote CI PASS; no remote repository/source upload is implied by this task.

Phase7B, Phase7.5 and Phase8 are not started by infrastructure readiness.

## Later graphics isolation (2026-10-08)

Hardware host/NVIDIA passed initial smoke and five targeted revalidations, but a
later7A long-run recorded approximately99-second GPU/frame stalls and Home/onStop
timeout. Keep the failure; original root cause remains unconfirmed. For isolation,
use the SAME AVD with `--graphics software`; actual Google SwiftShader was verified,
boot22062ms. This normal SDK fallback changes no image, ABI, APK, driver or Windows
setting. Prefer the backend validated for the scenario; the earlier host preference
is not a claim of stable hardware long-run support. Label all software/translation
measurements and retain physical GPU acceptance separately. See ADR018 and7A report.
