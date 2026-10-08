# Phase 4 renderer

`FrameSource` supplies raw 240×160 RGBA8888. `OriginalSurface` remains the existing
public View name and now accepts immutable `DisplaySettings`. Neither shader selection
nor viewport touches Session/Core. Enums are shared in core-api; data owns the singleton
DataStore and the ViewModel orders writes. Missing file defaults Sharp/Fit. Unsupported
enum/schema/corrupt bounded contents use Original/Fit. No save/Room schema migration.

Pause menu → Settings provides black / white player background. The same preference
colors the Compose container and GLES viewport margins; source game pixels and raw
images are unaffected. DisplaySettings encoding version 2 adds BackgroundTone, while
version 1 remains readable with black as its default. The existing DataStore file and
ordered writer are reused. Portrait system-bar icon contrast follows the background.

Original: embedded minimal GLES2 shader, NEAREST. Audit-friendly shader assets mirror
the embedded baseline; fallback does not depend on an asset read.
Sharp: LINEAR with fractional edge reconstruction: texel center +
`(fraction - clamp(fraction, -range, range))*scale`, `range=.5-.5/scale`.
Integer output pixel centers resolve to the original texel center; fractional edges
are positive-weight interpolations. No negative weights, ringing or extra sharpening.

GBA Color: `linear=pow(rgb,3.2)*.94`; `out=pow(clamp(M*linear,0,1),1/2.2)`:

```text
M = [ .820  .240 -.060
      .125  .665  .210
      .195  .075  .730 ]
```

Source: public-domain [gba-color.glsl](https://github.com/libretro/glsl-shaders/blob/77697c58448380156a87b251e6461d5ab00071f3/handheld/shaders/color/gba-color.glsl),
Pokefan531/hunterk. Fixed source, fixed coefficients; no runtime fetch. Gamma incorporates
the source's target gamma 2.2 plus darken 1.0. Contrast and saturation are identity;
offsets zero; neutral white has output approximately .972 and black is zero. Clamping
before fractional power avoids NaNs for negative matrix contributions. This community
approximation of an unlit GBA LCD is not phone-panel or individual-console calibration.

LCD: center 96% plus average of horizontal neighbours 4%, followed by ≤6% pixel-grid
attenuation. Grid fades out below 2×. One pass, three texture samples, no history,
ghosting, FBO, bloom or colour gamut change. Test tiny 3×5 original glyphs and checkerboard.

Fit uses the largest 3:2 rectangle in whole pixel units and centers it; at most two
extra edge pixels are unused. Integer uses maximum whole 240×160 multiple; below 1×,
Fit. Portrait shows system bars and applies safeDrawing to content. Landscape hides
system bars and draws the Surface edge to edge without extra outer padding; unused
Fit/Integer space uses the selected black / white background. Touch controls retain safeDrawing around cutouts and
transient bars. Input-profile coordinates and storage format are retained. Full-window
Surface does not imply stretching or cropping the 3:2 game image; choose Fit to maximize
the complete image, while Integer deliberately uses whole source-resolution multiples.

One context owns one texture and a lazy cache of ≤4 programs; two CPU buffers are
reused. ADR-013 replaces GLSurfaceView with TextureView and a renderer-owned EGL14
HandlerThread, paced by that thread's Choreographer. UI lifecycle/resize callbacks only
enqueue commands and never wait for a GL frame/swap. Pause destroys the context; resume
recreates it. Destroyed SurfaceTextures are released after EGL cleanup. A shared display
owner prevents one Surface's teardown from terminating a neighbour's context. New
context creation drops stale names before constructing resources. Partial shaders and
programs are deleted on the same GL thread. Built-in compile/link/setup failures select
Original, delete the failed program, log Debug diagnostics, and post a user notice and
Original preference update on the UI thread. Original's minimal baseline presumes a
working GLES2 driver; complete driver/EGL failure cannot be repaired by a shader fallback.

Release checks errors only at mode/setup transitions, not every frame. Debug snapshots
contain context/viewport/mode/live-resource counts, draw count, CPU submission elapsed
including driver stalls, and a fence-completion wait sampled once every 120 draws.
The latter is an upper bound that includes driver/backlog, not a timer-query GPU execution
duration. Display skip lower bounds compare produced frames and draws; they do not count
every duplicate upload and are not exact drop counts. Distinguish these limits in reports.

Screenshots/State thumbnails still use raw framebuffer and contain no shader/UI.
Tests use original Apache-2.0 patterns, deterministic raw frame 300 and actual TextureView
Surface content capture plus UiAutomation screen pixels (not cross-GPU hashes), repeated rotation, lifecycle/context recreation,
real compile/link/GL-setup faults, FF and rewind. Commercial/uncleared ROMs are excluded.
