# Phase 2 save format — schema 1

All paths below are inside Android `filesDir`. Native receives bounded bytes, never these paths.
Game ID is lowercase SHA-256 of ROM content. Display names and URIs never determine save paths.

```text
files/
├── roms/<game-id>.gba                    # private immutable ROM snapshot
└── persistence/<game-id>/
    ├── battery/
    │   ├── current.json                 # schemaVersion/current/previous UUIDs
    │   ├── <current-uuid>/battery.sav    # raw interoperable battery bytes
    │   ├── <current-uuid>/metadata.json # schema/gameId/length/SHA-256
    │   └── <previous-uuid>/...           # last known valid .sav backup
    ├── slot-1/...                       # slots 1–4
    ├── quick/...                        # independent of slot-1
    ├── auto-a/...
    ├── auto-b/...
    └── auto-c/...

state generation contents: game.state + metadata.json
Room: save-metadata.db (metadata only, schema 2; explicit migration 1→2)
```

Battery is not a State and is not embedded in Room. Maximum battery size 128 KiB; State 2 MiB;
JSON 16 KiB, strict parsing, nesting limit 8, primitive fields only, schemaVersion=1.
State metadata includes gameId/core/coreVersion/coreCommit/stateVersion/createdAt/appVersion/
slot/kind/stateSize/stateSha256/sequence. Exact mGBA 0.10.5 commit and stateVersion 7 are required.
Only storage-generated UUID directories and fixed slot keys are followed. JSON paths are never used.

Writes create an immutable UUID generation, flush/fsync binary+JSON, verify size/hash, fsync generation,
then fsync temporary manifest and atomically replace `current.json`. Directory fsync follows commit.
Same-directory atomic rename is required; failure never falls back to direct overwrite. A process-wide
commit lock serializes repository instances during ViewModel replacement. Filesystems/hardware may
still vary in guarantees after a sudden power failure; no universal power-loss guarantee is claimed.

Battery read validates current then previous generation. If both fail, startup stops before creating
a new battery save. Recovery shows a message. Corrupt/incompatible generations are retained.
Successful writes prune only unreferenced validated compatible generations. Startup cleans only
private `current.json.tmp.<uuid>` files; orphan/corrupt generations remain available for investigation.

Manual/quick read reports corruption/mismatch/incompatibility; never silently loads an old overwrite.
Autosave reads A/B/C, picks descending sequence of valid compatible candidates, and tries the previous
candidate if native loading rejects the latest. State loading preserves current battery memory and
never imports historical battery bytes. Missing/unusable autosaves do not block normal battery startup.

`StateScreenshotWriter` was reserved in Phase 2. Phase 3 writes separate private
`thumbnails/<game-id>/slot-N.webp` and `.time` capture timestamps after a State commit.
Only a matching State createdAt timestamp permits display; missing/stale/failed images are hidden.
Failure of this hook cannot invalidate a committed state. Explicit screenshots are independent
240×160 framebuffer WebP files under private `screenshots/`; no Android overlay or EXIF.
Rewind snapshots are memory-only and never use this storage layout. Rewind/background/close checkpoints
flush current battery but skip State autosave if rewind was actively held; periodic checkpoints defer
while rewinding. A user-initiated State load retains current battery and clears old rewind history.

Future schema changes require explicit migrations. Keep `test-rom/test-save-v1.sav` unchanged and test
old battery import/export on every future version. Unknown schema/core identities are rejected without
deleting user data. Native core upgrades must separately review State compatibility.
