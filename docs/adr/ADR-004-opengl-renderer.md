# ADR-004-opengl-renderer

Status: Accepted
Date: 2026-10-06

Decision: standalone GLES2 renderer, 240x160 RGBA8888 nearest texture, aspect ratio letterboxing.
Reusable direct buffer; no Bitmap allocation, CPU scaling or configurable shader system.
Surface recreation creates fresh GLES objects, source buffers stay owned by core.
