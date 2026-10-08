# ADR-012 — Phase 4 display pipeline and preferences

Status: Accepted, 2026-10-07. Scope: Phase 4 only.

Keep GLES 2 and FrameSource → Renderer; mode/scale changes never recreate Session or Core.
Each context owns one RGBA texture and at most four programs, with two reusable CPU
buffers and no FBO. Context creation discards stale names; all GL work belongs to the
GLSurfaceView thread. Original is compiled first and remains the fallback. Built-in
shader failures delete partial objects and notify the application to persist Original.
No user-supplied shader source or runtime network path exists.

Original uses NEAREST. Sharp reconstructs only the fractional edge between texels
with integer-aware sampling and no negative weights (no sharpening/halo).
LCD uses a 4% neighbour blend and a maximum 6% pixel-grid attenuation, without history.
GBA Color independently implements the public-domain community formula documented in
https://raw.githubusercontent.com/libretro/glsl-shaders/77697c58448380156a87b251e6461d5ab00071f3/handheld/shaders/color/gba-color.glsl
(Pokefan531/hunterk): input gamma 3.2, luminance .94, fixed matrix, output gamma 2.2.
This approximates an unlit GBA LCD; it is not a measured calibration of this phone or
every GBA model. Contrast/saturation are identity, black is zero, white stays neutral.
The exact coefficients and formula are retained in project source and Renderer docs.

PROJECT_SPEC and ARCHITECTURE prescribe DataStore for settings. That overrides the
lower-priority Phase 4 recommendation to prefer zero new dependencies. Add only
official AndroidX DataStore core 1.1.7 (Apache-2.0), with a bounded custom immutable
serializer, application singleton and pinned locks/checksums; no protobuf/plugin or
toolchain upgrade. Display enums live in core-api so data need not depend on Renderer.
Unknown values/corrupt settings fall back to Original/Fit; new installations use Sharp/Fit.
Settings writes are ordered by the ViewModel, separate from game saves and input profiles.

Screenshots and thumbnails retain raw RGBA; save/state formats remain unchanged.
Viewport is centered 3:2, Fit with no crop, Integer largest 240×160 multiple or Fit.

Debug-only diagnostics measure CPU submission and sparsely sampled glFinish completion
wait (an upper bound, not a timer-query GPU execution measurement). Device acceptance
also records Android Window FrameMetrics GPU_DURATION where supported, explicitly
distinguishing window GPU work from the separate GLES Surface. Missing precise Surface
GPU timing must be disclosed; no telemetry/native SDK is introduced. Real Surface
pixels, resource bounds and frame throughput are acceptance evidence, never native
frame counters alone. Release performs no per-frame error checks or timing fences.

Use only Apache-2.0 project-authored visual ROMs in this change, with source/ROM hashes.
External corpus suggestions remain candidates until revision and all asset rights are audited.

## User feedback amendment — background preference (2026-10-07)

Provide a Settings entry in the pause menu with black / white background. Persist the
background alongside display settings using the existing DataStore, without a new
dependency or permission. DisplaySettings encoding version 2 adds this value; version 1
continues to decode with a black default. This is unrelated to Battery/State/Room schemas.
Use the same chosen color for the player container and GLES viewport margins. Game
pixels, shaders, scaling, raw screenshots and thumbnails remain unchanged. Existing
display-settings entry remains available; portrait system-bar icon contrast follows the
background. This records the approach before implementation.
