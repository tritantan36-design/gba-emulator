# ADR-019 — Lite ROM boundaries and fixed native backing

Accepted 2026-10-08. Scope: GBA Lite's own import and native FD adapter.

The previous importer accepted any image >=192 bytes with byte0xB2=0x96.
The pinned native GBA detector also requires byte3=0xEA. A header-only image could
therefore create an unusable Library snapshot. Pinned VFame detection reads through
0x16B even on a short power-of-two image; its raw-parser historical finding remains
preserved. App production input must not rely on an allocator's accidental spare bytes.

Import and both Kotlin/native FD entry points require at least256 bytes. Import also
checks the native initial-branch signature. This deliberately rejects header-only
images while retaining the project's332-byte bringup and401-byte persistence ROMs.

For a native image shorter than512 bytes, the owned vector is zero-extended to512;
the const VFile still exposes its original logical length. The source file, SHA256,
GameId, logical CRC input and persisted formats remain unchanged. The minimum256-byte
power-of-two fixture exercises load/close via JNI with both known and unknown lengths.
Existing tiny legal ROMs continue through the ordinary full JNI regression.

An initial512-byte import/native minimum incorrectly rejected these legal homebrew
fixtures:8 JNI failures. That candidate is superseded, retained as evidence and is
not the delivery build. There is no claim that every >=256-byte signed image is a
valid game or that all pinned-core bounds problems have been audited.

Fixed local fixtures replace neither an upstream fix nor a full ASan/fuzz campaign.
Upstream mGBA/Oboe sources and provenance manifests are unchanged. Advanced work is
DEFERRED — PRE_PUBLIC_RELEASE_HARDENING; see the Lite report and deferred inventory.
