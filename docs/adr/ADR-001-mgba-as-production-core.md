# ADR-001-mgba-as-production-core

Status: Accepted
Date: 2026-10-06

Context: GBA-only, auditable offline Android application.
Decision: official mGBA stable 0.10.5 at 26b7884bc25a5933960f3cdcd98bac1ae14d42e2, built from source.
Why mGBA: established GBA accuracy, C core API, maintained upstream.
Why not Libretro: unnecessary frontend abstraction and unrelated platform surface.
Why SkyEmu is deferred: V1 explicitly has one core, replaceable through EmulatorCore.
License implications: own code Apache-2.0; mGBA MPL-2.0 and its bundled notices retained.
Upgrade policy: explicit release+SHA update, compatibility and save regression required.
Acquisition: Git transport failed; official commit archive is vendored with SHA-256 in SOURCE_LOCK.json. No upstream modifications or binary downloads.
