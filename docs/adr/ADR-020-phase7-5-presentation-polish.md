# ADR-020 — Phase 7.5 presentation polish

2026-10-08. Accepted for implementation, manual acceptance pending.

The user explicitly requests completing Phase7.5 before combined Phase7/7.5
acceptance. This overrides the task document's READY-first and intermediate
Round A approval schedule. It does not waive either stage's acceptance gates.
Round A and B remain separate implementation/verification checkpoints.

Reuse app -> feature-player dependencies for shared presentation tokens and
small components; no new module, runtime dependency or copied visual asset.
Light neutral application chrome and graphite game controls/overlays use a
single restrained accent. Only native GBA A/B/L/R/Start/Select are shown; no
fictional X/Y or L2/R2 mapping. Saved touch profiles/default positions remain
unchanged. Shoulder/system-button paint and hit shape are derived from the same
geometry; D-pad dead zone/slide, pointer ownership and InputRouter are unchanged.

Pause root offers six actions, with separate save/load/speed/display pages.
Pause/audio/input safety completes before the overlay appears. Actions continue
to call the existing Session/Persistence API; no second implementation. Settings
navigation uses one top back hierarchy; root tabs retain bottom navigation.
Artwork stays local, bounded and decoded off the UI thread.

No Core/Session/renderer/audio/sensor pipeline, import validation, game identity,
save/schema, permissions or network changes. Screenshot evidence, native input,
real TextureView pixels, UI persistence and protected-source hashes are verified.
Physical comfort/HID/audio/sensors and Phase7 remaining game coverage stay pending
the user's combined acceptance. No Phase8/signing/release authorization is implied.
