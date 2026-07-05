# Sakuro Worklog

This file is a compact English record of implementation sessions, decisions, checks, and remaining work.

## 2026-07-05 (Session 12)

**Anime4K CNN port to Media3** covered phases 1-2/4 of `docs/anime4k-media3-port-plan.md`. `sakuro-bench` showed that the previous Media3 anime path, a one-pass unsharp-like effect, produced almost no objective gain, while real Anime4K shaders in `engine-mpv` reached much higher VMAF.

Key conclusion: this is not an ML problem. Anime4K weights are embedded in GLSL as `mat4(...)`; the task is a generic mpv/libplacebo-style multi-pass render-graph runtime over Media3's GL pipeline.

### Phase 1: Pure Core Without GL (`481ff5c`)

- `RpnExpression` evaluates `//!WIDTH`, `//!HEIGHT`, and `//!WHEN` formulas in reverse Polish notation.
- `UserShaderPass` and `MpvUserShaderParser` parse `.glsl` files into shader passes from `DESC`, `HOOK`, `BIND`, `SAVE`, `WIDTH`, `HEIGHT`, `COMPONENTS`, and `WHEN`.
- `Anime4KGraphPlanner` statically plans the graph from source size, computes intermediate sizes, skips false `WHEN` passes, and returns the final `MAIN` size.
- `ShaderPreamble` generates mpv-compatible uniforms and helpers for every bind so shader bodies compile without edits.
- Unit coverage was added for RPN, parser, and planner behavior.

### Phase 2/4: GL Runtime And Integration (`263fc38`)

- `Anime4KShaderProgram` implements an internal render graph inside one `GlEffect`, rendering each pass to its own `GL_RGBA16F` FBO.
- `Anime4KGlEffect` wraps the runtime and is no-op for empty chains.
- `Anime4KChain` chooses S/M models and the canonical Clamp -> Denoise -> Restore -> Upscale order, loading vendored `.glsl` assets from `engine-mpv`.
- `UpscaleEffectChain.build(context, ...)` now returns `Anime4KGlEffect` for ANIME/CARTOON profiles and the legacy path otherwise.
- `Media3PlayerEngine` passes `applicationContext`.
- `:composeApp:compileFossDebugSources`, detekt, and unit tests passed.

### Phase 3: Stages, Validation, Real Shaders (`86e0941`)

- `PREKERNEL` and `NATIVE` are canonicalized to `MAIN`, matching the evolving-frame behavior needed by Clamp, Restore, Upscale, and final presentation.
- The planner validates every `BIND` against known stages or earlier `SAVE` outputs.
- `RealShaderGraphTest` runs all six real `assets/anime4k` shaders through parser, planner, and preamble generation.

### Adaptive Shader Toggle (`94b4650`, `e7e3f72`)

Added `SakuroSettings.adaptiveEnabled`. When disabled, the selected preset is applied unchanged with adaptive level 0, and `AdaptiveController.reset()` clears internal state. The settings switch lives in Debug. The debug overlay shows adaptive disabled state.

### Device Log Fixes (`ccfcf4f`, `1a022f8`, `65d7ce4`)

- Added HOOKED-to-stage aliases so mpv-style `HOOK MAIN` + `BIND HOOKED` shader bodies can call helpers such as `MAIN_texOff`.
- Avoided crashes from inactive uniforms optimized out by GLSL compilers.
- Gated uniform writes with `glGetUniformLocation >= 0` while still satisfying Media3 `GlProgram` active-uniform binding requirements.

### Remaining

- Real-device visual verification and Media3-vs-mpv comparison through `sakuro-bench`.
- Earlier FP16 capability detection before effect construction.
- Performance profiling on real hardware.

## 2026-07-04 (Session 11)

Added a premium library/catalog experience with directories, sorting, and Google Photos-like UX.

- `VideoItem.folderName` and Android `BUCKET_DISPLAY_NAME` support.
- `LibraryOrganizer` provides pure sorting, folder grouping, and date sections.
- `LibraryPreferencesStore` persists sort selection.
- `LibraryComponent` adds Videos/Folders segments, folder drill-down, and system back handling.
- `LibraryScreen` adds segment control, sort bottom sheet, sticky section headers, folder cards, and animated tab switching.

Refresh fix:

- `MediaLibrary.requestSystemRescan()` forces an Android `MediaScannerConnection` scan over public media folders.
- `MediaStoreVideoLibrary` observes `Video.EXTERNAL_CONTENT_URI`.
- Manual refresh and observer reload were separated to avoid scan/observer loops.

Checks: detekt passed, desktop compile passed, `compileFossDebugKotlinAndroid` passed, and new library tests passed.

## 2026-07-03

The repository became a working v0.1 app after earlier documentation-only state.

- KMP + Compose Multiplatform for Android and Desktop.
- Decompose, Koin, Lucide, and brand theme.
- Media3/ExoPlayer engine with GLSL ES effects through `setVideoEffects`.
- Fake desktop player engine.
- Library, player, settings, and debug overlay.
- Build command: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :composeApp:assembleFossDebug`.

Implemented content detection and adaptive control:

- `:core:core-detect` with `ContentClassifier -> Flow<ContentDetection>`.
- `FilenameContentClassifier` for fansub tags, keywords, and release naming.
- `AdaptiveController` for thermal, battery, power-save, and dropped-frame degradation.
- Android `DeviceStatusMonitor`; desktop static fallback.
- `PlaybackHealthTracker` computes dropped-frame percentage from `DebugStats`.
- `PlayerComponent` combines debug stats, device status, selected preset, and detection.
- Auto preset selection now reflects content class, and the debug overlay shows detection/adaptive state.

Checks: unit tests, `assembleFossDebug`, and desktop compile passed. Emulator runtime verification was deferred.

## 2026-07-03 (Session 2)

Implemented player gestures.

- Pure gesture math in `ui/gestures`: seek swipes, level swipes, and pinch mode hysteresis.
- `detectPlayerGestures` integrates swipes and pinch while preserving tap and long-press behavior before touch slop.
- `PlayerSystemControls` controls Android brightness and volume; desktop uses an in-memory stub.
- `ScaleMode` FIT/FILL/ZOOM maps to player resizing.
- Central gesture badge shows seek delta, level percentage, or frame mode.
- Settings include a "player gestures" toggle.

Runtime on Pixel 9a confirmed detection, Auto preset selection, real upscale output, adaptive thermal degradation, seek/volume/brightness gestures, and a z-order fix for the gesture badge.

Commits: `29887bc`, `d385e94`.

## 2026-07-03 (Session 3)

Implemented frame-sample content detection (`bda4d80`).

- `FrameSampler` and `FrameSample` are engine-independent.
- Android `RetrieverFrameSampler` samples five frames from 10-90% of duration.
- `FrameContentClassifier` uses flat-fill ratio, quantized palette richness, and gradient-histogram shape to classify drawn versus live-action content.
- `CompositeContentClassifier` runs layers in parallel and exposes only non-worse results.
- Android DI combines filename and frame detection.

Checks: unit tests, `assembleFossDebug`, desktop compile, and Pixel 9a runtime cases for synthetic anime/live-action samples.

## 2026-07-03 (Session 4)

Build infrastructure and Inter font.

- Added `build-logic` with precompiled convention plugins for KMP libraries, Android libraries, and detekt.
- Migrated modules to convention plugins.
- Tuned detekt for Compose, gesture state machines, detection thresholds, and Decompose DI constructors.
- Added Inter Regular/Medium/SemiBold/Bold to compose resources and wired Material 3 typography.
- Stored Inter OFL license in `licenses/inter/LICENSE.txt`.

Checks: detekt, unit tests, `assembleFossDebug`, desktop compile, and Pixel 9a visual font check.

## 2026-07-03 (Session 5)

Added Coil thumbnails and desktop frame sampling.

- Added `coil-compose` and `coil-video` 3.3.0 on Android.
- `VideoThumbnail` uses `AsyncImage` with `videoFramePercent(0.2)`.
- `SakuroApplication` registers `VideoFrameDecoder`.
- `FfmpegFrameSampler` on desktop uses `ffprobe` and `ffmpeg` via `ProcessBuilder`.
- Desktop detection now mirrors Android when local files and ffmpeg are available.

Checks: detekt, unit tests, `assembleFossDebug`, desktop compile, and Pixel 9a thumbnail rendering.

## 2026-07-03 (Session 6)

Implemented smaller gesture-related features.

- Android enters PiP when the app is backgrounded during playback, instead of using a downward swipe that would conflict with brightness/volume gestures.
- Added swipe sensitivity settings.
- Preserved sane desktop stubs where platform features do not exist.

## Continuing Work

- Finish and verify `engine-mpv`.
- Calibrate content-detection thresholds on real libraries.
- Profile Anime4K Media3 runtime on real hardware.
- Add earlier FP16/device capability gating.
- Continue polishing library, player, presets, and settings.
