# ADR-011 — One ordered lifecycle authority

Date: 2026-10-06. Accepted before implementation.

Phase 3 background/foreground regression exposed duplicate lifecycle commands from Activity and
Compose. A cancelled Compose effect retained its NonCancellable Session checkpoint and could finish
after a newer foreground effect, leaving a resumed Activity with a paused Session.

Activity is the sole foreground authority. Its ViewModel queues lifecycle events on a channel with
one coroutine consumer, preserving callback order across IO suspension. Compose only resumes/pauses
Surface and releases touch sources. Session remains the owner of native/persistence operations;
its existing Mutex and NonCancellable safety are retained. A user-pause StateFlow lets rebuilt UI
open the pause menu after rotation without automatically resuming the user's paused core.

No changes to upstream, disk formats, permissions or toolchain. Re-run normal and paused rotations,
FF/rewind background/foreground, persistent exit/resume and UBSan. The failed regression is retained.
