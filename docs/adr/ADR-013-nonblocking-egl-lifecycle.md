# ADR-013 — Nonblocking EGL lifecycle

Accepted 2026-10-07, Phase 4. Supersedes ADR-012's GLSurfaceView lifecycle ownership.

Real device combination tests reproduced an ANR. The test watchdog recorded the main
thread WAITING in GLSurfaceView.GLThread.onWindowResize, reached through SurfaceView
layout, and GLThread RUNNABLE inside EGLImpl.eglSwapBuffers. Evidence is retained in
Phase 4 logs. No core/storage/input worker held the UI thread in the captured stack.
This does not establish the root cause of the historical Phase 3 whole-screen black event.

Use TextureView with one renderer-owned HandlerThread and explicit GLES2/EGL14 ownership.
UI callbacks only enqueue create/resize/pause/resume/destroy commands; UI never waits for
GL or swap. Preserve OriginalSurface's public configuration/lifecycle API. Keep the same
FrameSource, shaders, reusable buffers and Session. No native library/dependency/permission
or toolchain changes. TextureView compositing is a tradeoff; measure real FF/render cost.
The GL thread's Choreographer supplies vsync callbacks, avoiding a fixed sleep after swap.

All EGL/GL work runs on that thread. Destroy context on pause, recreate on resume. A
destroyed SurfaceTexture is retained until EGL cleanup, then released exactly by the
renderer thread. HandlerThread is shut down after queued cleanup on detach. Resize only
updates buffer size/viewport; no synchronous render acknowledgement on UI.

Actual-pixel tests capture TextureView's rendered texture content with getBitmap; this
is real Android Surface content, not the native framebuffer. Existing UiAutomation
orientation checks continue and locate OriginalSurface by its public class. No overlay
changes, save/image-format changes or Core/Session recreation. Extend regression to
24 rotations and forced context loss/background/resume before any READY conclusion.
