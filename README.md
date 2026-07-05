# Sakuro

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF.svg)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.8.2-4285F4.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Android](https://img.shields.io/badge/Android-minSdk%2026-3DDC84.svg)](composeApp/build.gradle.kts)
[![Status](https://img.shields.io/badge/status-early%20alpha-f5a623.svg)](#project-status)
[![FOSS](https://img.shields.io/badge/FOSS-friendly-2ea44f.svg)](#licensing)

![Sakuro neon logo banner](assets/branding/sakuro-readme-banner.png)

Sakuro is a Kotlin Multiplatform video player focused on local playback and real-time upscaling.

The Android app is the main product target. The Desktop/JVM target is kept as a fast UI and architecture sandbox, using a fake player engine where native Android playback is unavailable.

## Highlights

- Local video library on Android through MediaStore, plus manual file opening through the system picker.
- Two Android playback engines behind a shared `PlayerEngine` abstraction:
  - **Media3/ExoPlayer** for native Android playback, hardware decoding, and GLSL ES effects through Media3 video effects.
  - **libmpv** through `dev.jdtech.mpv:libmpv`, with native shader support and broad codec coverage.
- Real-time upscale presets for anime, cartoon, and live-action content.
- Media3 Anime4K shader runtime that parses mpv-style user shaders and executes a multi-pass render graph.
- Debug overlay with engine, decoder, codec, resolution, FPS, dropped frames, bitrate, audio, active preset, detection, adaptive state, and device signals.
- Content detection from filenames and sampled frames, with automatic preset selection and manual override.
- Adaptive playback controller for thermal, battery, power-save, and dropped-frame conditions.
- Gesture-first player controls: tap, double-tap seek, long-press speed, seek swipe, brightness/volume swipe, and pinch scale modes.
- Settings backed by multiplatform settings: engine choice, default preset, debug overlay, adaptive mode, gesture controls, and preset import/export.
- `foss` and `full` Android flavors. They are currently equivalent and do not include proprietary SDKs.

## Project Status

Sakuro is an early Android-first open-source project. It is suitable for development, testing, and technical review, but the playback and shader pipeline should still be treated as young software.

Verified locally:

- Android debug and release builds.
- Unit tests across core modules, Media3 shader planning/parsing, mpv property mapping, library organization, settings, gestures, detection, and adaptive control.
- Media3 playback with live preset switching and upscaled output on an emulator.

Known practical limits:

- Real-device profiling for the Media3 Anime4K path is still important before calling the shader runtime production-stable.
- Native libmpv packaging increases APK size and makes licensing/distribution review more important.
- DRM/protected streams are out of scope because decoded frames are not available to custom effects.
- Desktop is a UI sandbox, not a full desktop media player.

## Screens And Experience

The app is designed around a quiet full-screen player, a focused local library, and advanced controls that stay out of the way until needed. The visual system uses Compose Material 3, custom Sakuro colors, Inter typography, and Lucide icons.

Useful project documents:

- [ARCHITECTURE.md](ARCHITECTURE.md) - module boundaries, engine strategy, flavors, and licensing notes.
- [FEATURES.md](FEATURES.md) - product feature map and implementation ownership.
- [DESIGN.md](DESIGN.md) - interface principles, layout direction, and visual tokens.
- [BRAND.md](BRAND.md) - name, voice, logo, palette, and asset rules.
- [CHANGELOG.md](CHANGELOG.md) - notable changes by version.
- [PRIVACY.md](PRIVACY.md) - local data handling and benchmark privacy notes.

## Build And Run

Requirements:

- JDK 17 or newer.
- Android SDK.
- `local.properties` with `sdk.dir=...` for Android builds.

Build the Android app:

```bash
./gradlew :composeApp:assembleFossDebug
```

Install the debug APK:

```bash
adb install -r composeApp/build/outputs/apk/foss/debug/composeApp-foss-debug.apk
```

Build a release APK:

```bash
./gradlew :composeApp:assembleFossRelease
```

Run the desktop UI sandbox:

```bash
./gradlew :composeApp:desktopRun
```

Run tests:

```bash
./gradlew test
```

Run static analysis:

```bash
./gradlew detekt
```

## Benchmarking

Sakuro includes `tools/sakuro-bench`, a local benchmark and comparison utility for shader work. It is useful when checking Media3 output against mpv-based reference renders and when measuring whether an upscale path actually improves objective quality instead of only adding GPU cost.

```bash
cd tools/sakuro-bench
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python -m sakuro_bench --help
```

Generated captures and reports are local artifacts and are ignored by git.

See [tools/sakuro-bench/README.md](tools/sakuro-bench/README.md) for the full methodology, metric definitions, capture workflow, report format, and limitations.

## Module Layout

```text
composeApp/            Compose UI, navigation, Android entry point, Desktop sandbox
core/core-player/      PlayerEngine API, state, tracks, debug stats, engine registry
core/core-upscale/     Upscale profiles, built-in presets, stores, adaptive controller
core/core-media/       Media library model, Android MediaStore integration, desktop samples
core/core-detect/      Filename and frame-sample content classification
core/core-settings/    Persisted app settings
engine/engine-media3/  Media3/ExoPlayer engine and GLSL ES upscale effects
engine/engine-mpv/     libmpv engine, shader store, mpv property mapping
engine/engine-fake/    Fake engine for desktop UI and tests
build-logic/           Shared Gradle convention plugins
tools/sakuro-bench/    Local benchmark utility for shader/output comparisons
licenses/              Third-party license texts for vendored assets
assets/branding/       Source branding assets
```

## Licensing

Sakuro is distributed under the [GNU General Public License v3.0](LICENSE) because the Android app links against libmpv. Vendored Anime4K shaders and Inter font assets keep their original licenses under [licenses/](licenses/).

Review [ARCHITECTURE.md](ARCHITECTURE.md) for licensing notes around engines, flavors, and third-party dependencies.

## Contributing

Small, focused contributions are welcome: build issues, documentation corrections, reproducible playback bugs, benchmark improvements, and test-backed engine fixes are easiest to review.

Before opening a pull request, read [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

For security reports, please follow [SECURITY.md](SECURITY.md) and do not open a public issue.

When reporting playback or quality problems, include:

- Device/OS details for playback and shader issues.
- Engine selection: Media3 or mpv.
- Video codec, resolution, and container when relevant.
- Logs or screenshots for visual/rendering bugs.
