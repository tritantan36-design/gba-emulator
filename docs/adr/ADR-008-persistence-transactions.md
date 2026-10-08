# ADR-008 — Phase 2 persistence and lifecycle

Status: Accepted, 2026-10-06. Scope: Phase 2 only.

Battery save safety takes priority over state convenience. Core exports/imports bounded bytes using official mGBA savedataClone/savedataRestore APIs; Android paths remain in storage. No upstream changes or toolchain upgrades.

Storage commits immutable generation directories containing raw battery.sav or raw state plus schema-1 JSON and checksums. A small current.json manifest names the current and previous generations. Files are flushed/fsynced before a same-directory ATOMIC_MOVE replacement of the manifest. The previous valid generation is the backup; this avoids a two-file state/metadata commit gap. If atomic replacement is unavailable, fail closed. Directory fsync is attempted on Android; hardware/filesystem power-loss durability remains platform-dependent. No old valid generation is deleted during a failing operation. Recovery follows only validated UUID names, never paths from metadata. Orphan generations are retained; only private *.tmp.* files are cleaned.

States use raw mCore stateSize/saveState/loadState, deliberately excluding battery bytes. Loading any state preserves the current battery bytes; autosave cannot roll back normal .sav. State version 7 and exact pinned core identity must match before JNI parsing. Screenshots have an optional isolated interface, with failure independent of state success.

Room 2.7.2 stores only Game/LastSession/SaveState metadata; storage manifests are authoritative and Room indexes can be rebuilt. Gson 2.13.2 is used only for bounded JSON objects. Defaults Autosave/Quick Resume stay ON; no settings UI or DataStore needed this phase.

The pilot Room schema 1 is retained with an explicit 1→2 migration adding source size/modification tokens (defaults -1). Save/state JSON stays schemaVersion 1; raw battery data is unaffected. No destructive migration is enabled.

An Activity ViewModel owns Session and application-context repositories. No global native singleton or Activity references. Configuration recreation retains the paused session; final ViewModel clearing uses serialized non-cancellable save/close. Background/exit flush battery then autosave; periodic battery checkpoint every 45 seconds. Normal exit/ROM switch is refused if battery persistence fails, preserving the live core for retry.

ROM selection copies bounded SAF bytes to an app-private immutable content-addressed ROM cache while hashing on IO. Cached identity is reused during Activity recreation and process relaunch when the SAF provider returns an unchanged nonzero modification token and size. Unknown/missing/changed provider tokens force bounded rehashing; changed contents become a new game. Providers that lie about modification tokens remain an external limitation. No storage scanning or extra permissions.

Sources: https://developer.android.com/jetpack/androidx/releases/room#2.7.2 ; https://github.com/google/gson/releases/tag/gson-parent-2.13.2 ; retained official mGBA core.h, gba/core.c, gba/serialize.c.
