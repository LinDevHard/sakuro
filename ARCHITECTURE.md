# Sakuro Architecture And Stack

> Project: **Sakuro** (`com.rinwave.sakuro`)
> Date: 2026-07-02
> Status: recorded stack decisions.
> Related: [DESIGN.md](DESIGN.md), [FEATURES.md](FEATURES.md), [BRAND.md](BRAND.md).

---

## 1. Recorded Decisions

| Area | Decision |
|---|---|
| Name | **Sakuro** |
| Package / namespace | **`com.rinwave.sakuro`**; modules use `com.rinwave.sakuro.*` |
| Platform / stack | **Kotlin Multiplatform (KMP)** with shared code and shared UI |
| Targets | **Android** as product, **Desktop/JVM** as UI sandbox, **iOS** in Phase 3 |
| UI | **Compose Multiplatform** across targets and engines |
| Navigation / components | **Decompose** for component-based navigation and lifecycle |
| DI | **Koin** multiplatform |
| Images / previews | **Coil 3** for Compose Multiplatform |
| Build | Gradle convention plugins in `:build-logic`, plus version catalog |
| Code quality | **detekt** + **ktlint** through convention plugins and CI |
| Adaptivity | Window size classes and canonical adaptive layouts for phones, tablets, resizable windows, and foldables |
| Platform base | **AndroidX**: Media3, DataStore, lifecycle, etc. |
| Playback engines | Two switchable engines in settings: **libmpv** and **Media3 (ExoPlayer)** |
| Abstraction | A single `PlayerEngine` interface with engine-specific implementations behind it |
| Upscaling | Anime4K / ArtCNN. libmpv uses native `.glsl`; Media3 ports passes to GLSL ES |
| Flavors | `foss` for F-Droid and `full` for Play Store |
| Distribution | Google Play Store + F-Droid |
| License | GPL because of libmpv linking |
| First phase | Local media only; no streaming or DRM |
| Design | Premium media-player UI, strong typography, Lucide icons |

ExoPlayer is the player inside Media3, not a separate engine. "Two engines" means libmpv and Media3/ExoPlayer.

## 2. Why Two Engines

The user can choose the engine in settings because the engines cover different needs.

- **libmpv** supports Anime4K/ArtCNN user shaders out of the box, has broad codec support, and can be shared across Android and iOS. The cost is native C wrapping and GPL licensing.
- **Media3 (ExoPlayer)** is native Android, Apache 2.0, integrates well with Compose, and has ready infrastructure for formats, adaptive playback, and DRM. The cost is Android-only scope and the need to port upscale shaders to GLSL ES through `setVideoEffects()` and custom `GlEffect` / `GlShaderProgram` code.

A shared UI sits above both through `PlayerEngine`.

## 3. Module Layout

```text
gradle/libs.versions.toml   version catalog
build-logic/                convention plugins for KMP, Compose, Android, flavors

composeApp/                 app UI, navigation, platform entry points
  src/commonMain            Compose UI, Decompose components, Koin wiring, Coil
  src/androidMain           Android Activity and flavors; depends on Android engines
  src/desktopMain           JVM UI sandbox; depends on engine-fake
  src/iosMain               iOS entry in Phase 3; planned libmpv dependency

core/                       KMP logic libraries without UI
  core-player/              PlayerEngine, PlayerState, tracks, controller, DebugStats
  core-upscale/             UpscaleProfile/Preset, built-ins, import/export, AdaptiveController
  core-detect/              ContentClassifier for anime/cartoon/live-action
  core-media/               local library model, metadata, scanner
  core-settings/            engine, preset, theme, and app settings

engine/                     PlayerEngine implementations
  engine-media3/            Android-only Media3/ExoPlayer + GLSL ES effects
  engine-mpv/               libmpv for Android/iOS; NDK/cinterop work is isolated here
  engine-fake/              FakePlayerEngine for desktop and tests

iosApp/                     Xcode wrapper in Phase 3
```

Core logic lives in `core/*`; UI, navigation, and entry points live in `composeApp`; each engine is isolated under `engine/*` and depends only on `:core:core-player`. `composeApp` wires available engines per flavor/target through Koin.

### 3.1 JetBrains Alignment

- `composeApp` + `iosApp` follows the current KMP wizard layout for shared Compose UI.
- The default hierarchy template owns intermediate source sets such as `appleMain`.
- Targets are `androidTarget()`, `jvm("desktop")`, and iOS targets.
- Interfaces + DI are preferred over `expect/actual` unless a declaration is truly platform-specific.
- Version catalog + convention plugins keep repeated module configuration consistent.

### 3.2 Desktop Target

Desktop/JVM exists as a fast UI sandbox with Compose Hot Reload. It uses `FakePlayerEngine` by default, enough to exercise UI, navigation, player state, tracks, and the design system without native playback dependencies.

## 4. Engine Abstraction

```kotlin
interface PlayerEngine {
    val state: StateFlow<PlayerState>
    val debugStats: Flow<DebugStats>
    fun load(media: MediaItem)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun selectTrack(track: TrackSelection)
    fun applyUpscale(profile: UpscaleProfile)
    fun release()
}

enum class EngineType { MPV, MEDIA3 }
```

UI depends only on `PlayerEngine` and `PlayerState`. Engine switching requests the selected factory from Koin and recreates the engine. `applyUpscale()` hides implementation differences: libmpv loads `.glsl`, while Media3 builds and applies a `GlEffect` list.

## 5. Adaptive Upscaling

`AdaptiveController` decides the active `UpscaleProfile` from device class, thermal status, battery/power-saving mode, actual FPS, and dropped frames. It can move to heavier presets when there is headroom, degrade to lighter Anime4K modes when the device heats up or drops frames, and disable processing under critical thermal pressure.

## 6. Flavors And F-Droid

The Android app has two product flavors:

- **`foss`** for F-Droid, with no Firebase, Crashlytics, Play Services, AdMob, or proprietary SDKs.
- **`full`** for Play Store, optionally with opt-in crash reporting and no proprietary dependency in the core.

F-Droid requirements are designed in from day one: FLOSS toolchain, reproducible build, isolated libmpv native build recipe, and dependency separation so `foss` never pulls proprietary SDKs.

## 7. Licensing

- libmpv linking makes the product GPL.
- Anime4K is MIT; ArtCNN licensing must be checked before vendoring.
- Media3 is Apache 2.0.

The repository license is GPLv3 because of libmpv. The value is in product quality and UX rather than closed code.

## 8. Monetization

Supported options are donations, GitHub Sponsors, Open Collective, Liberapay, or an optional paid support/pro Play Store listing. Feature paywalls are intentionally avoided because this is FOSS.

## 9. Phases

- **Phase 1:** Android, local media only, libmpv + Media3 selectable in settings, base Anime4K/ArtCNN presets, premium Compose UI, `foss`/`full` flavors, F-Droid release, and desktop UI sandbox with `FakePlayerEngine`.
- **Phase 2:** adaptive controller for thermal/battery/FPS behavior, universal presets, and SoC-class optimization.
- **Phase 3:** iOS target through libmpv + shared Compose UI, followed by non-DRM streaming.

## 10. Open Technical Questions

- Compose Multiplatform iOS stability by Phase 3.
- libmpv KMM build: Android `.so`, iOS `.framework`, JNI, and cinterop bridges.
- Media3 upscaling: output `Size` control in a `GlEffect` chain and workarounds for `setVideoEffects()` limitations.
- Settings storage: `multiplatform-settings` versus DataStore on Android plus an iOS equivalent.

## 11. Stack Summary

| Library | Role | Notes |
|---|---|---|
| Kotlin Multiplatform | shared logic and UI | Android now, iOS later |
| Compose Multiplatform | declarative UI | shared across platforms and engines |
| Decompose | navigation and component lifecycle | correct back/state/process-death handling |
| Koin | dependency injection | multiplatform |
| Coil 3 | images and previews | thumbnails and covers |
| Material3 Adaptive | adaptive layouts | window size classes and navigation suites |
| detekt + ktlint | code quality and style | convention plugin + CI |
| AndroidX | platform base | Media3, DataStore, lifecycle, core-ktx |
| Media3 | Android playback engine | `setVideoEffects()` + `GlEffect` |
| libmpv | cross-platform playback engine | GPL, native shader support |

All dependencies in the `foss` flavor must be free software and free of proprietary transitive SDKs.

## 12. Code Quality And Tooling

- detekt runs static analysis through `config/detekt/detekt.yml`.
- ktlint formatting is provided through detekt-formatting or a single dedicated ktlint path; avoid duplicate rule systems.
- Both tools belong in convention plugins and apply consistently to `composeApp` and `core/*`.
- CI blocks merges on detekt and ktlint checks.
- A pre-commit hook may run fast checks on changed files.
- Compose-specific detekt rules should catch common composable naming, state, and modifier mistakes.
- `.editorconfig` is the single style source for ktlint.
