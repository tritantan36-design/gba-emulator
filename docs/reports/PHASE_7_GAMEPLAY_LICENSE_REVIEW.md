# Phase 7 gameplay license review

2026-10-08. No new third-party game ROM was downloaded, built, vendored or played.
Internal Apache-2.0 probes and the selected MIT mGBA suite remain the executed corpus.
Complete-gameplay coverage is still MISSING for platformer, action, audio-heavy,
high-load/3D, save-heavy and RTC/sensor. A source-code license does not establish
the redistribution rights of all bundled artwork, fonts, music and sounds.

| Candidate | Exact source reviewed | Findings | Acquisition / CI decision |
|---|---|---|---|
|BeatBeast v1.1.7|[release](https://github.com/afska/beat-beast/releases/tag/v1.1.7), source `b42b9c255eb278d05a42a1f54dfd8b1f94efe29c`|Code MIT; separate graphics/music/SFX notices include CC BY-NC 4.0, NC-SA 3.0 and asset-specific paid-license conditions. Official release LICENSES.zip downloaded and inspected; SHA256 `8862b2bbb52e72925921ad3444d9ec99cf3bc8dbd28cbe1a7b7eb1ea3b9ed7ef`.|ROM NOT_ACQUIRED; public redistribution and CI NOT_APPROVED.|
|Microjam23 1.02|[source at release](https://github.com/gbadev-org/microjam23/tree/0bca06987eb585b5f4becfde37f857b33c53033f)|Root MIT plus separate asset credits inspected at the release revision. Includes CC0/BY/SA/NC, CF Halloween personal-use font, custom sound terms and an unclear paid explosion asset license.|ROM NOT_ACQUIRED; public redistribution and CI NOT_APPROVED.|
|Varooom 3D|[Butano source](https://github.com/GValiente/butano/tree/e217fdde3220192daa8aed1a7e2034f83bf412c8/games/varooom-3d)|Read graphics/models/music/SFX/voice credits: substantial NC-SA terms, eJay voice copyright and some unresolved sound permissions. Reviewed source revision is not a tested binary release.|ROM NOT_ACQUIRED; public redistribution and CI NOT_APPROVED.|
|Goodboy Advance|[upstream repository](https://github.com/exelotl/goodboy-advance)|No complete redistribution license established by the inspected repository.|NOT_ACQUIRED; not an approved replacement.|

The local evidence directory retains fetched BeatBeast notices and Microjam asset
credits with source/hash records. License review is completed only to the extent
above; it is not a legal determination or permission to redistribute these games.
No commercial ROM, BIOS or user game was collected or uploaded. The six gameplay
gaps remain open; no 10–30 minute representative game session was fabricated.
The user may exercise their own lawful local games for final personal-use
acceptance without uploading a ROM. Such feedback has not yet been received.
