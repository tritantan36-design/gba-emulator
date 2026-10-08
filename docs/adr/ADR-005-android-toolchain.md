# ADR-005-android-toolchain

Status: Accepted
Date: 2026-10-06

Decision: minSdk 26, compileSdk/targetSdk 36; AGP 8.13.0, Gradle 8.14.3, Kotlin 2.2.20, JDK21.
NDK 27.2.12479018, CMake 3.22.1, arm64-v8a only.
Reason: modern Android scope and AAudio availability; Oboe still allows OpenSL fallback.
Versions are pinned, dependency resolution locked. Release has no debug signing configuration.
No release publication until a private production key and all specification gates are supplied.
