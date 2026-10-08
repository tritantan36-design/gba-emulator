# ADR-018 — Official emulator environment for ordinary Phase 7A API QA

Date: 2026-10-07. Status: Accepted for isolated development QA only.

The application remains arm64-v8a only. Do not add x86 production builds, replace
the core, or change JDK/NDK/CMake/Gradle/dependencies to obtain an emulator pass.
No permissions, system driver installations or Windows feature changes.

Official Android SDK inventory captured in
docs/reports/evidence/phase7/7a-emulator-package-inventory.log lists emulator
37.2.12 and Google APIs API 31 x86_64 image revision 14. These specific revisions
may be installed in the existing D:/GPT/tools/android-sdk, alongside frozen build
tools. Record installed package metadata/hashes; never run an unbounded SDK update.
Additional image revisions require the same recorded provenance.
Installed API 31 package.xml reports revision 14, while the official archive's
source.properties reports 12. Preserve both values as an upstream metadata
discrepancy; the fetched archive was x86_64-31_r14.zip, not a silent version change.

Google documents ARM ABI support in Google APIs x86 system images:
https://android-developers.googleblog.com/2020/03/run-arm-apps-on-android-emulator.html
This is a candidate, not proof that every image supports this APK. After boot,
record the actual ABI list and native bridge. Proceed with arm64 APK tests only
if the unmodified official image supports them. If unsupported, report the gap;
do not alter ABI properties, image API metadata or emulator checks to force it.
Do not install unknown translation binaries.

The unmodified API 31 image boots with ABI list `x86_64,arm64-v8a` and native
bridge `libndk_translation.so`. WHPX is already installed/usable; no driver or
Windows feature changes were made. Inventory also pins candidate API 26 ARM64
revision 3, API 29 Google APIs x86_64 revision 13 and API 33 x86_64 revision 17.
These may be installed for the remaining representative groups. API 26 ARM64
uses the SDK's normal software CPU mode; do not disable architecture/API checks
if unsupported. Each result depends on actual boot/install/test evidence.

AVDs/data/evidence live in D:/GPT/tools/android-avd and project artifact directories.
Headless/software graphics are allowed normal SDK options. Emulator results must
be labelled emulator/translation, never native arm64 phone performance, physical
HID/sensor evidence or another physical OEM. A fresh emulator install does not
require clearing or uninstalling the user's phone application.

Emulator ADB commands always specify its serial; phone stress commands always
specify 3B15AV01Z0000000 once a second device exists. Do not disturb the phone long-run.

## Phase7A-0 supplement (2026-10-07)

Primary automated target is now official API34 Google APIs x86_64 revision14;
guest actually advertises x86_64,arm64-v8a and libndk_translation.so. No APK ABI
change is needed. Official archive SHA256:
783a40134baf4f3012d4464fbe1571b1612a0dbd2e7a44d14bd8328923443833.
Source/size/SHA1 and extracted-file verification are retained under evidence/emulator.
SDKManager transport stalled before receiving bytes; direct official Google archive
fallback retains original image files and faithful SDK package metadata. No mirrors.

The standard auto GPU choice used SwiftShader; an explicitly verified host backend
uses NVIDIA RTX4070 OpenGL ES Translator. Record both, and prefer the working host
backend for ordinary UI QA. This is an AVD option, not a Windows driver/security change.
Only one AVD runs during measured tests. Hardware GPU selection still does not make
ARM translation a physical-device performance or audio/sensor acceptance baseline.

After the primary chain passed, API36 Google APIs x86_64 revision7 and API26
Google APIs x86_64 revision16 were installed sequentially. The API26 x86_64 probe
is distinct from the prior unbootable ARM image: boot succeeds, original APK fails
NO_MATCHING_ABIS. Empty newly created API26 probe AVD removed after evidence capture.
Existing API26 ARM/29 boot/ABI limitations and API31 completed evidence are preserved
rather than replaced or repeatedly downloaded.
All frozen build/core/toolchain versions and arm64 production scope remain intact.

2026-10-08 ordinary7A isolation: the later host/NVIDIA long-run failed after
3004375ms/48 cycle samples. Logs show approximately99-second GPU/frame stalls,
PAUSED→STOPPED delayed approximately101s, then Home lifecycle wait timeout.
No original root cause is established; earlier hardware smoke/targeted passes and
failed long-run evidence are retained. No Windows driver/features/power changes.
Use the SDK's normal `-gpu software` option on the SAME official API34 image for
isolated repeat validation. Actual renderer identifies Google SwiftShader, boot
22062ms. Label resulting measurements software/ARM translation; do not claim
hardware GPU performance or silently resolve the historical/host graphics risks.
No production/core/ABI change or unknown translation component is involved.
