# Phase 0–2 security boundaries

The application is completely offline and requests zero permissions. ROMs use ACTION_OPEN_DOCUMENT,
content URI metadata and a bounded borrowed file descriptor. Native duplicates the descriptor and reads
at most 32 MiB, with a ten-second read deadline; no arbitrary file path or remote URL reaches mGBA.

Only `.gba` is accepted; ZIP parsing remains deferred. ROM inputs remain untrusted.
mGBA owns emulator execution, never host code execution. No shell, dynamic user libraries, DEX or plugins
are loaded by the app. Development scripts are not packaged in the APK.

mGBA 0.10.5 and Oboe 1.9.3 are built from official commit-pinned source. The exact upstream archive
retained file set and every file SHA-256 are audited. There are no upstream code patches.
Unused `cinema/` regression ROMs/video logs/commercial-game baselines are excluded explicitly (ADR-007).
The Compose dependency brings `libandroidx.graphics.path.so` from official
`androidx.graphics:graphics-path:1.0.1` on Google Maven; this is a known AndroidX runtime artifact,
not an emulator core downloaded from a third party. Its checksums are recorded in Gradle verification metadata.
The emulator bridge statically includes mGBA, Oboe and official NDK C++ runtime; no unknown `.so` is accepted.

Native handles are opaque increasing IDs, checked against a registry with shared lifetime ownership.
Published video frames are copied under a mutex into caller-owned buffers. Worker teardown joins before
destroying mGBA. Audio callbacks consume a bounded SPSC queue without JNI, allocation, disk IO or locks.

Release native flags: stack protector, FORTIFY where supported, full RELRO, NX, position-independent code,
16 KiB load segment alignment. Android process PIE is provided by the platform; a shared library is ELF DYN/PIC.
Debug ASan/UBSan configuration is reserved; Phase 2 ran NDK UBSan trap tests on a real arm64 device.
This stage does not claim a completed hostile-ROM/state fuzzing audit.

Release is unsigned until a private production key is configured. A debug key is never used for Release.
App logs are not uploaded. Current UI has no account, Ads, Analytics, networking or full library.
Phase 2 stores battery/state bytes only in private app storage, with immutable versions and atomic
manifest replacement. State JSON is bounded/strict, fixed keys/UUIDs only, with ROM/core/version and
SHA-256 validation. State load preserves the current battery bytes. Session serializes native operations.
Room stores metadata only, with an explicit non-destructive 1→2 migration. Process death can lose work
since the last completed checkpoint; no zero-loss or universal power-loss durability is claimed.
See SAVE_FORMAT.md and PHASE_2_PERSISTENCE_REPORT.md for actual guarantees and evidence.

Phase 3: ROM VFiles are fixed-size/read-only (ADR-010); Core retains their byte storage until deinit.
Rewind states/framebuffers stay in a bounded native ring (64 MiB payload, 16 MiB after low-memory trim),
with one bounded pending snapshot outside the ring. Restore clones/reinstates current battery; no rewind
files, persisted State rewrites or battery rollback. No Android Bluetooth/storage/vibration permissions.
Input ownership is per source, with explicit cancellation/disconnection/lifecycle release.
WebP encoding is a one-off copy of the emulated framebuffer in private storage, without EXIF/location.
No shader or sensor features, external skins, proprietary resources or network dependencies are added.
