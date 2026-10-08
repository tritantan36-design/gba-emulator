# Phase 7 test ROM license inventory

No test ROM or Nintendo BIOS is packaged in the production APK. This inventory
does not grant redistribution rights for unreviewed homebrew. Tier C/D content
must remain local and must never be uploaded to CI or committed.

| Corpus | Source / revision | Code / assets | Tier | Redistribution / CI |
|---|---|---|---|---|
| Internal bringup / persistence | test-rom/bringup.s, persistence.s; fixed hashes in historical manifests and evidence | Original Apache-2.0; no commercial assets or logo | A | Allowed with LICENSE / NOTICE; test APK only |
| Color / LCD patterns | test-rom/visual-patterns.py; video-tests.json fixes generated hashes | Original Apache-2.0; procedural images | A | Allowed; test APK only |
| RTC / Tilt / Gyro / Solar / rumble probes | test-rom/peripheral-probe.c, peripheral-probes.py; peripheral-tests.json | Original Apache-2.0; GPIO identifiers do not contain commercial code/assets | A | Allowed; disabled physical rumble remains deferred |
| Library / stress variants | library-tests.json / phase7-stress.json; original ROM plus identity suffix | Original Apache-2.0 | A | Allowed; isolated test identities only |
| mGBA suite selected shifter | https://github.com/mgba-emu/suite/tree/e6942030d25ffe3ba76c72b73a86da073ec857cc ; suite-selected.json | MIT, Copyright 2015 Jeffrey Pfau; only original instruction tests and expected table, no fonts/music used | A | Allowed with retained upstream LICENSE; selected subset only, test APK / host / CI |
| Full mGBA suite | Same pinned revision | MIT; full build not performed (missing devkitARM/libgba/grit) | A candidate | NOT TESTED; no prebuilt ROM acquired |
| NBA / FuzzARM / 160p | No revision acquired this phase | Not audited this phase | A/B candidates | Not fetched, built or included |
| BeatBeast / Varooom 3D / Microjam | Candidates in GBA_TEST_ROM_CORPUS.md | Mixed/restricted asset terms reviewed; see Phase7 gameplay license report | Not approved | ROM not fetched, built or included |
| Other local homebrew / user-owned commercial ROM | User-controlled local-only corpus | Individual rights require verification | C / D | NOT TESTED; no upload / commit / packaging |

Selected suite upstream files and complete MIT notice are retained in mgba-suite/.
The freestanding wrapper is project-authored Apache-2.0. Removed GNU .func metadata
is documented in ADR-017; expected values and tested instructions are unchanged.
No source-offer obligation beyond the retained MIT copyright/permission notice.

## Local staged Phase7 gameplay additions (2026-10-08)

Blob Goes 3D v1.1 and Hyperspace Roll from agb v0.25.0 official releases were
acquired locally. Source revisions, binary hashes, component licenses and conditional
redistribution/CI decisions: [inventory](../manifests/phase7-gameplay-local.json),
[asset review](../../docs/reports/PHASE_7_GAMEPLAY_LICENSE_REVIEW.md).
No game binary is included in this repository or any APK/CI artifact. Explicit
instrumentation arguments and exact binary hashes are required; ordinary runs do
not download or execute these games. This review does not approve other examples
in the agb release archive or previously restricted candidates.
