# Phase 7 gameplay license review

2026-10-08. Two official open-source homebrew binaries have now been acquired for
local, opt-in testing. No game binary, upstream examples archive, BIOS or raw device
capture is committed, uploaded to CI, or packaged in either APK. The pinned source,
release URL, size, hash, category and usage decision are recorded in
[local gameplay inventory](../../test-rom/manifests/phase7-gameplay-local.json).
This supersedes the earlier statement that no full game was acquired.

| Game | Exact source / assets reviewed | Acquisition / redistribution / CI |
|---|---|---|
|Blob Goes 3D v1.1|[release source](https://github.com/MilanFIN/blob-goes-3d/tree/5e5bbca3fd9708f7d7300a0b3ff6ddf3957fbbab). MIT, MilanFIN 2023. Source includes generated geometry/levels, original glyph lookup and PSG sound; no external sampled soundtrack found in the reviewed tree. Cargo dependencies retain their own notices.|Official v1.1 ROM acquired locally, 378752 bytes; fixed SHA in inventory. Platformer/Mode4 software 3D. Conditional redistribution with upstream/dependency notices; not vendored and not enabled in CI.|
|Hyperspace Roll, agb v0.25.0|[release source](https://github.com/agbrs/agb/tree/1c2842c954fe1dd40a4b0ce0fbecf4ab7157745a/examples/hyperspace-roll), root MPL-2.0; Pixelated font by Greenma201 CC-BY-SA-3.0. Root README additionally attributes example music CC-BY-4.0 and logo CC-BY-SA-4.0; agbabi subset zlib. Source references title/menu/battle looping WAV tracks and simultaneous SFX.|Only examples/hyperspace-roll.gba extracted from official v0.25.0 examples.zip, 6948016 bytes. Local test allowed within reviewed open-source/asset terms; CI vendoring/redistribution NOT_ENABLED pending full source/attribution package. Other games in the archive not approved by this review.|
|Solar Guard, GBA-JAM-2021|[pinned source](https://github.com/Deft-Spade/Solar-Guard/tree/7418bdbeccbc62366e1abd57526402990973d9a8). Code/original assets GPL-3.0; all 15 art acknowledgment files and soundtrack notice reviewed. Art mixed CC0/BY/SA/OGA-BY/NASA; music mentions OpenMPT/GM.DLS samples.|ROM NOT_ACQUIRED; sampled-music binary redistribution not cleared. No test result.|
|uCity Advance v1.0.3|[pinned source](https://github.com/AntonioND/ucity-advance/tree/2a52ef4d72e77f27563a4d1a67712dd8738e8f28). GPL-3.0 code, CC-BY-NC-SA-4.0 art; seven music credits and bundled ModArchive terms reviewed. ModArchive unbundled-sharing FAQ does not establish permission to bundle modules into games.|ROM NOT_ACQUIRED; save-heavy coverage not claimed. No CI approval.|

Earlier restricted candidates remain excluded:

| Candidate | Exact source reviewed | Findings | Acquisition / CI decision |
|---|---|---|---|
|BeatBeast v1.1.7|[release](https://github.com/afska/beat-beast/releases/tag/v1.1.7), source `b42b9c255eb278d05a42a1f54dfd8b1f94efe29c`|Code MIT; separate graphics/music/SFX notices include CC BY-NC 4.0, NC-SA 3.0 and asset-specific paid-license conditions. Official release LICENSES.zip downloaded and inspected; SHA256 `8862b2bbb52e72925921ad3444d9ec99cf3bc8dbd28cbe1a7b7eb1ea3b9ed7ef`.|ROM NOT_ACQUIRED; public redistribution and CI NOT_APPROVED.|
|Microjam23 1.02|[source at release](https://github.com/gbadev-org/microjam23/tree/0bca06987eb585b5f4becfde37f857b33c53033f)|Root MIT plus separate asset credits inspected at the release revision. Includes CC0/BY/SA/NC, CF Halloween personal-use font, custom sound terms and an unclear paid explosion asset license.|ROM NOT_ACQUIRED; public redistribution and CI NOT_APPROVED.|
|Varooom 3D|[Butano source](https://github.com/GValiente/butano/tree/e217fdde3220192daa8aed1a7e2034f83bf412c8/games/varooom-3d)|Read graphics/models/music/SFX/voice credits: substantial NC-SA terms, eJay voice copyright and some unresolved sound permissions. Reviewed source revision is not a tested binary release.|ROM NOT_ACQUIRED; public redistribution and CI NOT_APPROVED.|
|Goodboy Advance|[upstream repository](https://github.com/exelotl/goodboy-advance)|No complete redistribution license established by the inspected repository.|NOT_ACQUIRED; not an approved replacement.|


Local evidence in `evidence/phase7/gameplay-closeout/` retains exact source trees,
license/credit text hashes, release metadata and binary SHA records. Earlier BeatBeast,
Microjam and Varooom reviews remain in `evidence/phase7/remaining-gaps/`.
A local execution result does not authorize future public ROM distribution. Gameplay
coverage and limitations are reported separately in the compatibility matrix; no
human listening, physical sensor or 30-minute user acceptance is inferred.
