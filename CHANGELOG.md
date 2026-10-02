# Changelog

All notable changes to Sakuro are documented here.

This project follows a simple pre-1.0 changelog style: changes are grouped by version, and breaking behavior can still happen while the app is in early alpha.

## Unreleased

- Added a gated release pipeline for unsigned F-Droid APKs and signed Play APK/AAB artifacts, including tag/version validation, dependency-license auditing, optional R8/resource shrinking, mapping retention, and SHA-256 manifests.

### Added

- Open-source project documentation: license, contributing guide, security policy, code of conduct, issue templates, pull request template, privacy policy, funding metadata, and code ownership.
- README badges and clearer public project status.
- Fastlane quality/release lanes, localized F-Droid store metadata, and CI packaging checks.

### Changed

- New app icon: eclipse ring, play button and sakura blossom, with a matching monochrome variant for Android 13+ themed icons. All icon assets are generated from one script (`tools/branding/generate_app_icon.py`).
- Public README now reflects the current Android-first Media3/mpv architecture and links to benchmark methodology.
- Internal worklog/research notes are excluded from the public repository surface.
- The `foss` flavor excludes the prebuilt libmpv AAR and native mpv/FFmpeg libraries; the reference engine remains in `full` only.

## 0.1.0

Initial early-alpha Android-first build.

### Added

- Kotlin Multiplatform and Compose Multiplatform project structure.
- Android app target and Desktop/JVM UI sandbox.
- Local video library backed by Android MediaStore.
- Manual video opening through the system picker.
- Shared `PlayerEngine` abstraction and engine registry.
- Media3/ExoPlayer playback engine with GLSL ES video effects.
- libmpv playback engine with vendored Anime4K shader support.
- Fake player engine for desktop and tests.
- Built-in upscale presets and preset import/export primitives.
- Media3 Anime4K shader parser, graph planner, and multi-pass render runtime.
- Debug overlay for playback, engine, codec, resolution, FPS, dropped frames, bitrate, audio, detection, adaptive state, and active preset information.
- Filename and frame-sample content detection.
- Adaptive controller for thermal, battery, power-save, and dropped-frame conditions.
- Gesture controls for playback, seek, speed, brightness, volume, and scale mode.
- Settings backed by multiplatform settings.
- `foss` and `full` Android flavors.
- `tools/sakuro-bench` for frame-based upscale comparison and benchmark reports.
- Inter font and Sakuro branding assets.
- Unit tests for core logic, media library behavior, settings, detection, adaptive control, gestures, mpv mapping, and Media3 Anime4K planning/parsing.
