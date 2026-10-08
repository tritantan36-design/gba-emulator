# ADR-016 — Phase 6 library, import and real play time

Accepted before implementation, 2026-10-07. Phase 6 only; Phase 7 not started.

Keep the existing modules: app owns application coordination and Compose shell; data owns
Room metadata/SAF and local image lookup; storage owns pure JVM bounded import validation;
emulator-session owns serialized core/lifecycle/playtime. UI never owns JNI or save paths.
No dependency, permission, upstream core or frozen toolchain upgrade.

Room 2→3 explicitly adds addedAt, playTimeMs, artwork path and library visibility/availability.
1→2→3 remains supported. Removal hides the metadata row (retains historical time), removes
only its ROM snapshot/cache, and leaves battery, states, screenshots and LastSession history.
Hidden entries are not offered as Continue; reimport unhides without losing saves/time/name.
Import adds metadata without starting a session or updating LastSession. Duplicate hash keeps
metadata and provides Open. Relink requires an exact ROM hash; mismatch commits nothing.
The core receives the validated private snapshot's `.gba` filename; user-editable display
names and ZIP source names are metadata, never a substitute for the ROM filename/type.

GameId remains lowercase SHA-256 of uncompressed GBA bytes. Private snapshots are preferred
without rereading a revoked URI. A changed known provider modification token triggers bounded
rehash; different content becomes a different identity, except explicit relink which rejects it.
Missing snapshot rebuilds from source; failure keeps an unavailable library entry and saves.

ZIP uses standard java.util.zip plus strict bounded central/local-header validation, no external
extraction paths. Reject ZIP64, encryption, multidisk, nested archives, unsafe/duplicate names,
unsupported entries and inconsistent/truncated directory records. Exactly one .gba, optional
README.txt/LICENSE and directories. Limits: input 40 MiB, expanded total 40 MiB, ROM 32 MiB,
64 entries, ratio 200:1 per entry/total, UTF-8 path 240 bytes, depth 8, read budget 10 seconds.
Validate CRC and actual expanded sizes for every entry. Private random temps; cleanup on
failure/cancellation; sync and same-directory atomic snapshot commit before transactional index.
No untrusted path is ever used as an output path. No ROM blob in Room.

Session uses injected monotonic milliseconds and records only valid-frame foreground RUNNING
time. Pauses/settings/background/loading exclude time; FF/rewind count real time. Accumulate
on transitions and flush at checkpoints/end; DB failure retains pending time for retry.
Last Played is updated once after first real frame, not import/details/checkpoint.

Artwork order: manual thumbnail, per-game screenshot, autosave thumbnail, original vector
cartridge placeholder. Decode on IO to small dimensions; bound shared cache to 2 MiB.
Existing screenshots lack game identity and are not guessed into unrelated game artwork.
Unified settings reuse the existing display/peripheral stores and input profiles, expose only
implemented capabilities; Rumble is explanatory text under V1 zero-permission policy.
Preserve compatible high-frequency player controls and consolidate settings destinations.

All previous reports/logs/hashes stay historical. Document automated and manual acceptance
separately. No destructive migration, save deletion, production signing or Phase 7 work.
