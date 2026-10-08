# ADR-003-core-adapter-boundary

Status: Accepted
Date: 2026-10-06

Decision: UI -> InputRouter/EmulatorSession -> EmulatorCore -> MgbaCoreAdapter -> JNI.
Only app composition root constructs the concrete adapter. Feature code sees interfaces.
GameSource carries a borrowed descriptor and length, not Android types or filesystem paths.
Core duplicates/reads bounded FD input during load; caller closes its PFD after load completes.
Frames are copied into a reusable direct RGBA8888 buffer through FrameSource, never exposed pointers.
Native registry uses monotonic opaque IDs and shared ownership to reject stale handles safely.
Session serializes lifecycle on a coroutine dispatcher; frame execution stays on a native worker.
Phase 0/1 API deliberately omits persistence, rewind, fast forward, and sensors.
