# ADR-007: exclude unused upstream regression assets

Status: Accepted
Date: 2026-10-06

Context: the official mGBA commit archive includes `cinema/`: binary GB/GBA test
ROMs, video logs and commercial-game image baselines. This phase uses only the
project-authored Apache-2.0 Homebrew, and the specification requires a clean ROM
provenance record and no commercial ROMs in the repository.

Decision: exclude the entire unused `cinema/` directory from the vendored source
snapshot. No C/C++ source, headers, upstream build files, license or copyright
notices are altered. No runtime feature or module dependency changes.

The official archive SHA-256 remains recorded. SOURCE_LOCK.json declares the exact
excluded prefix; file manifests retain hashes from the official archive, and the
audit compares the retained file set and every retained file byte for byte.
The original archives are retained outside this repository in the workspace's
temporary acquisition directory, not checked in or packaged.

The source-built library and existing tests do not use these assets, so removal
does not change compiled emulator behavior. The only test `.gba` in this project
is generated from `test-rom/bringup.s` and included exclusively in androidTest.
