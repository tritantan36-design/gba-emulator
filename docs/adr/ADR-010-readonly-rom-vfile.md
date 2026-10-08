# ADR-010 — Fixed-length read-only ROM VFile

Date: 2026-10-06. Accepted before implementation.

Phase 3's original test-only ROM suffix caused an Android crash in CRC32 during mGBA
ROM detection/loading. The fixed upstream's multiboot detector decodes instruction-like
trailing bytes and probes literal locations via seek. VFileMemChunk's expanding seek is
appropriate for battery storage, but inappropriate for a read-only ROM: probes can
change file size and allocation, including integer-overflow-sized probes.

Keep the fixed upstream unmodified. Core owns the bounded ROM bytes for its whole
lifetime, and supplies VFileFromConstMemory, whose seek is bounded and whose write is
disabled. mGBA owns/ closes the VFile; Core's vector remains alive until after core deinit.
ROM SHA-256 still uses original bytes, without padding or rewriting. No new core,
permissions, toolchain, disk format or version change. Battery remains a mutable VFile.

Add regression for literal-like trailing bytes in a legal homebrew. Rerun native/JNI,
Activity, persistence, UBSan and upstream hash audit. Historical Phase 0/2 acceptance
is retained as historical evidence; this new boundary finding is disclosed in Phase 3.
