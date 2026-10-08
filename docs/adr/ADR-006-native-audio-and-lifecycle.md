# ADR-006-native-audio-and-lifecycle

Status: Accepted
Date: 2026-10-06

Decision: audio module owns a bounded SPSC stereo PCM ring and Oboe callback.
Oboe source version 1.9.3 pinned to upstream commit in SOURCE_LOCK.json, statically built.
mGBA outputs 32768 Hz; Oboe converts to device rate using medium resampling quality.
Pause waits for worker quiescence, stops stream, then clears queue; resume starts audio before worker.
Callback performs no JNI, allocation, mutex, filesystem or logging work.
Core shutdown joins worker and closes stream before releasing mGBA resources.
Player resumes its Surface before requesting Session foreground resume. Activity onStop requests pause immediately.
Player exit pauses audio/core, pauses the Surface, then asks Session to close the adapter.
ROM loading is bounded to 32 MiB, descriptor reading supports pipe-backed SAF providers.
No SRAM or state files are created: Persistence not implemented until Phase 2.
