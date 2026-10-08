# Phase 7A-0 Android Emulator Infrastructure Report

Date: 2026-10-07 (Asia/Shanghai). **PASS_WITH_NOTE — INFRASTRUCTURE READY**.
This is Phase7 infrastructure, not a new product phase. Prior Phase7 results and
the immutable pause archive remain intact; no new Phase7B/7.5/8 work is implied.

## A. Host Environment

Windows11 Home10.0.26300, x64 Intel i7-12800HX,16GiB RAM; NVIDIA RTX4070 Laptop
GPU driver32.0.16.1062. D: approximately145GB free at discovery. Evidence:
`evidence/emulator/environment.txt`. Android Studio was not found in standard
installation locations; the existing command-line SDK is used.

## B. Existing Android Tooling

SDK `D:/GPT/tools/android-sdk`, AVD home `D:/GPT/tools/android-avd`.
Cmdline-tools19.0 installed under latest; adb37.0.1 (protocol1.0.41), emulator37.2.12.
Existing platform36 and build-tools35/36 retained. JDK21.0.12.1+1, Gradle8.14.3,
AGP8.13.0, Kotlin2.2.20, NDK27.2.12479018, CMake3.22.1 and all core/dependency
versions unchanged. minSdk26, target/compileSdk36. Existing26/29/31/33 images and
historical31 AVD are retained; no mass reinstall or cleanup.

## C. Virtualization Status

Elevated read-only `emulator -accel-check`:0, WHPX installed and usable.
HypervisorPresent=true. CIM VirtualizationFirmwareEnabled=false under the active
hypervisor is recorded, not taken as a reason to alter BIOS. Sandboxed accel-check
returned6, while the normal elevated process confirms usable acceleration.
No Windows features, security policy, drivers or BIOS settings changed.

## D. Installed Components

Sequential official Google APIs x86_64 images: API34 revision14 (primary),
API36 revision7, API26 revision16 (host-compatible availability probe).
SDKManager stalled at0-byte download; stopped before official curl fallback.
Official repository XML and matching archive checksum/size required before extraction.
Evidence: `evidence/emulator/api34-image-provenance.json` (installation result).
No Play Store image or build-tool upgrade. Existing API26 ARM image is distinct
from the new x86_64 probe; its prior boot failure evidence remains unchanged.

## E. Primary API 34 AVD

GBA_Lite_API34, Pixel5,1080x2340/440dpi,2GiB RAM,4vCPU,2GiB data partition,
serial emulator-5570. No Play Store. Source and AVD config are archived.

## F. API 34 Boot / adb Result

PASS. Actual boot_completed=1 and ADB device visible. Actual ABI x86_64,arm64-v8a;
bridge libndk_translation.so. Initial auto GPU chose SwiftShader, boot57684ms.
Standard host GPU verified NVIDIA RTX4070 OpenGL ES Translator, boot90831ms.
Both logs preserved; no permanent offline state. Only one AVD runs at a time.

## G. Build / Install / Launch Result

Required Debug and existing app androidTest build PASS in6s, mostly up-to-date:
`evidence/emulator/build-log.txt`. Does not repeat prior JVM/host test suites.
Install, force-stop, uninstall, reinstall and launch PASS on this isolated AVD.
Initial launches exceeded am-start wait (Displayed after15.187s/13.580s); logs retained,
no app crash/ANR found. After baseline, cold launch1567ms; reinstall launch1926ms,
both Status:ok. Actual package arm64-v8a, permissions unchanged/no INTERNET.

## H. Instrumentation Result

PASS3 tests/26.098s: Library home/navigation, Persistence battery/manual4slots/Quick/
Activity recreate/resume, and Renderer actual Home stops audio/foreground resumes.
Existing tests reused unchanged; no full-suite rerun. Log:instrumentation-result.txt.

## I. SAF Smoke Result

PASS actual DocumentsUI picker, cancel/reopen, Downloads self-authored GBA and
single-ROM ZIP import. Both success UI states archived. Picker portrait/landscape
transition and return preserved selection flow; not a full process-death picker test.
Existing ActivityMonitor test results are distinguished from this actual picker evidence.

## J. Lifecycle Smoke Result

PASS Home/foreground audio/frame invariant from existing smoke, portrait/landscape/
reverse actual app UI and screenshots, force-stop/relaunch and reinstall launch.
First fullscreen tutorial was actually observed, dismissed using Got it; initial
overlay screenshot preserved and separate clean captures saved. No duplicate
audio/core behavior observed by the selected smoke; not full stress certification.
No60-minute long-run in7A-0. Resource/audio counters do not prove audible quality.

## K. Screenshot / Logcat Evidence

`scripts/android_emulator.py` supports binary PNG capture, full/PID-filtered logcat,
UI XML and exact observed text taps. Explicit emulator serial and owned AVD name
are required; physical devices are refused. Actual PNGs viewed, game red framebuffer
and touch controls visible. Full/PID-filtered logcat and empty crash buffer saved;
no app-origin FATAL/ANR found in this window. Tombstones only if available after a
crash; none produced here. Native-crash test intentionally not introduced.

## L. API 26 Result

UNAVAILABLE for the existing arm64 APK on the available API26 environment.
Reuse original evidence `evidence/phase7/7a-api26-software-boot.log`: QEMU2 emulator
does not support arm64 CPU architecture on this x64 host. No repeated install,
architecture bypass or toolchain/image patch. Existing GbaLite_QA_API26 preserved;
closest already-working older ARM-bridge representative is API31. API26 product
compatibility remains NOT_TESTED, not PASS.
To establish the requested x64-host availability, official x86_64 revision16 was
then installed and booted:34445ms, actual ABI x86_64,x86. Original arm64 APK install
fails NO_MATCHING_ABIS. Play/save smoke therefore NOT_TESTED. New empty
GBA_Lite_API26 AVD removed after evidence capture; original GbaLite_QA_API26 retained.
First avdmanager cleanup encountered shutdown locks; only the verified new residual
directory was removed after the emulator exited. Both cleanup records preserved.

## M. API 29 Result

Existing official Google APIs x86_64 image revision13 booted previously but actual
arm64 production APK install failed NO_MATCHING_ABIS (actual guest ABI x86_64,x86).
Reuse GbaLite_QA_API29 and `evidence/phase7/7a-api29-device.txt`, `7a-api29-install.log`;
no duplicate AVD or download. Product play/save/orientation smoke NOT_TESTED.
This is an explicit ABI coverage gap, not an application crash or emulator PASS.

## N. API 36 Result

PASS_WITH_NOTE. GBA_Lite_API36, emulator-5572, official x86_64 revision7;
actual ABI x86_64,arm64-v8a and libndk_translation.so. Host/NVIDIA backend,
boot76138ms, initial cold launch4643ms (Status:ok), subsequent1766ms/2315ms.
Actual DocumentsUI GBA import PASS. Three existing minimal instrumentation tests
PASS29.295s (navigation, save/load/recreate/continue/exit, Home audio/frame invariant).
Actual portrait/landscape/reverse PNGs saved and viewed. Uninstall PASS, AVD stopped.
An additional host-script Exit tap failed to locate an offscreen node; not PASS.
Instrumentation exit includes scrolling and passed. Host GLES warnings0x501/0x502
observed during lifecycle/shutdown; no app crash/ANR found or visible black screen
in this smoke. This is infrastructure coverage, not full GPU/normal-game acceptance.

## O. Gradle Managed Devices

DEFERRED (optional). Ordinary AVD+adb now works with explicit GPU/serial control;
ARM bridge availability must be checked per exact image. Avoid a second ATD/GMD
image and uncontrolled concurrent memory load on16GiB host. Frozen AGP retained;
Installed AGP8.13.0 ManagedDevices/ManagedVirtualDevice classes confirmed in
`gmd-api-availability.txt`; no unsupported-toolchain claim, no remote GMD run claimed. GMD can be added later
with pinned image and equivalent GPU/ABI/result evidence; not needed for local chain.

## P. Physical-device-only Matrix

See `../ANDROID_TEST_DEVICE_STRATEGY.md`: actual Bluetooth/USB HID, motion/light
sensors, OEM lifecycle/Doze, real GPU, latency/artifacts/audio routes, thermal/battery,
final touch and30–60min gameplay require physical evidence. Emulator never substitutes
these acceptances. No phone operations in this infrastructure run.

## Q. Changed Files

Taskbook copied into project; new `scripts/android_emulator.py`,
`scripts/install_official_android_image.py`, `docs/ANDROID_TEST_DEVICE_STRATEGY.md`,
this report and `evidence/emulator/`; README and ADR018 infrastructure supplement.
No production Kotlin/C++/Manifest/Gradle implementation, core or ABI change.

## R. Known Issues

Previous API31 ARM-translation performance failures remain failures; infrastructure
success cannot automatically close them. SDKManager download transport stall retained.
Fallback installer initially failed after extraction at XML root lookup; recovered
only after verifying every extracted file size/CRC against the checksum-verified
official archive. Helper corrected; image contents were not modified. First cold
launch delay retained separately from later successful Status:ok launches.

## S. Deferred Items

No remote repository or actual remote CI run. CI configuration alone is not PASS.
Suspended7B findings unchanged. Stopped phone long-run is not restarted.

## T. Final Decision

**Phase7A-0 — PASS_WITH_NOTE**. Primary API34 chain is demonstrated.
**Android Emulator Infrastructure — READY**.
**Primary Automated Android Target — API34 Emulator**.
**Physical Device — Final / hardware-specific acceptance only**.
Notes: API26/29 arm64 coverage unavailable on these official x64 images; GMD/remote
emulator CI deferred. Existing Phase7 pause archive untouched. Continue Phase7A
from remaining failures, not by rerunning valid completed work.
Phase7 remains NOT READY; Phase7.5/8 NOT STARTED; V1 Release NOT READY.

### Subsequent7A note (2026-10-08; initial infrastructure results retained)

Later host/NVIDIA long-run recorded an approximately99-second graphics stall and
Home/onStop timeout. Root cause unconfirmed; initial smoke results are not erased
or expanded into long-run approval. Same official API34 AVD with normal SDK
software GPU option identifies Google SwiftShader (boot22062ms).50 acknowledged
Home/checkpoint cycles and100 actual shader changes subsequently PASS337.991s;
complete software long-run subsequently PASS3614.31s,62 samples/60 cycles/complete
event. See7A report for full metrics and retained host-failure risks.
No application/core/ABI/driver change in this backend isolation;7A-0 decision is
infrastructure readiness only, not physical GPU or complete stability acceptance.
