# Sakuro

> A video player with real-time upscaling. **Sakuro** by **Rinwave** (`com.rinwave.sakuro`).
> Open source under GPLv3 because of the planned libmpv integration; see [ARCHITECTURE.md](ARCHITECTURE.md) §7.

Kotlin Multiplatform + Compose Multiplatform: Android is the product target, Desktop/JVM is the fast UI sandbox.
Project documents: [ARCHITECTURE.md](ARCHITECTURE.md) · [FEATURES.md](FEATURES.md) · [DESIGN.md](DESIGN.md) · [BRAND.md](BRAND.md) · [RESEARCH.md](RESEARCH.md)

---

## Status (v0.1.0)

Phase 1 is a working build with **one production engine: Media3/ExoPlayer**.

- **Media3 engine** (`engine/engine-media3`): local video playback, hardware decoding, and upscaling through `ExoPlayer.setVideoEffects()` with a `GlEffect` chain:
  - `Presentation` raises the output resolution for the upscale pass.
  - `SharpenGlEffect` is a GLSL ES luma-guided unsharp mask with anti-ringing, modeled as a simplified Anime4K pass.
  - `DenoiseGlEffect` is a bilateral-lite edge-preserving denoise pass.
- **FakePlayerEngine** (`engine/engine-fake`) supports desktop and UI debugging without native dependencies.
- `PlayerEngine` + `EngineRegistry` keep UI independent from the concrete playback engine.
- Presets (`core/core-upscale`): Off / Anime SD / Anime HD / Live-action light, import/export serialization through `ProfileCodec`, and live switching from the player.
- Local video library through MediaStore, plus manual file opening through SAF.
- Debug overlay ("stats for nerds"): engine, decoder, codecs, source-to-output resolution, fps, dropped frames, bitrate, audio, and active upscale passes.
- Gestures: tap for controls, double tap for seek ±10s or pause, long press for 2x speed, and seek-bar scrubbing.
- Settings: engine selection, default preset, debug overlay, backed by `multiplatform-settings`.
- `foss` / `full` flavors are currently identical and contain no proprietary SDKs, so the project remains F-Droid-ready.
- Decompose navigation, Koin DI, Lucide icons, and the brand palette from [BRAND.md](BRAND.md).

Verified on an emulator (Pixel 9a, API 36): playback, live preset switching, 2x upscaling from `854x480` to `1708x960`, debug overlay, navigation, and permissions.

## Build And Run

Requires JDK 17+ and Android SDK. The SDK path is read from `local.properties`.

```bash
# Android (foss flavor)
./gradlew :composeApp:assembleFossDebug
adb install -r composeApp/build/outputs/apk/foss/debug/composeApp-foss-debug.apk

# Desktop (UI sandbox with FakePlayerEngine)
./gradlew :composeApp:desktopRun
```

## Module Layout

```text
composeApp/            UI (Compose Multiplatform), Decompose navigation, Android/Desktop entry points
core/core-player/      PlayerEngine, PlayerState, DebugStats, EngineRegistry
core/core-upscale/     UpscaleProfile/presets, ProfileCodec import/export
core/core-media/       library model, MediaStore scanner on Android, desktop samples
core/core-settings/    settings through multiplatform-settings
engine/engine-media3/  engine #1: Media3/ExoPlayer + GLSL ES upscale effects on Android
engine/engine-fake/    FakePlayerEngine for desktop and tests
```

## Known Differences From ARCHITECTURE.md

- **`engine-mpv` (libmpv) is not implemented yet.** It is the second Phase 1 engine and requires an NDK-built `.so` plus a JNI bridge. `EngineType.MPV` exists and appears in settings as coming soon.
- **`build-logic`, detekt, and ktlint** are present as project direction, while some configuration still lives in module build files over the version catalog.
- **`core-detect` and thermal/battery adaptation** are Phase 2 scope.
- **Inter variable font, Coil 3 thumbnails, and the full gesture set** (brightness/volume swipes and pinch) remain Phase 1 TODOs.
- Media3 nuance: when video effects are active, `onVideoSizeChanged` may not fire. The source size is read from `player.videoFormat`; live effect changes are applied with a quick re-prepare while restoring playback position.
