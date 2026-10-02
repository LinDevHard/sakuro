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
| Targets | **Android** as product, **Desktop/JVM** as UI sandbox, **iOS** after the Android pipeline stabilizes |
| UI | **Compose Multiplatform** across targets and engines |
| Navigation / components | **Decompose** for component-based navigation and lifecycle |
| DI | **Koin** multiplatform |
| Images / previews | **Coil 3** for Compose Multiplatform |
| Build | Gradle convention plugins in `:build-logic`, plus version catalog |
| Code quality | **detekt** + **ktlint** through convention plugins and CI |
| Adaptivity | Window size classes and canonical adaptive layouts for phones, tablets, resizable windows, and foldables |
| Platform base | **AndroidX**: Media3, DataStore, lifecycle, etc. |
| Playback engine | **Media3 (ExoPlayer)** is the product runtime |
| Reference engine | **libmpv** is a developer/reference tool for shader comparison, capture, and parity testing |
| Abstraction | A single `PlayerEngine` interface keeps Media3, fake, and reference implementations testable |
| Upscaling | mpv-compatible shader families are evaluated with libmpv and ported into Media3 GLSL ES / `GlEffect` runtimes |
| Flavors | `foss` for F-Droid/product builds and `full` for developer/reference builds with libmpv |
| Distribution | F-Droid first; any future Play build should reuse the Media3-only product dependency graph |
| License | GPL while libmpv remains linked in the repository; product packaging should avoid shipping libmpv unless that tradeoff is accepted |
| First phase | Local media only; no streaming or DRM |
| Design | Premium media-player UI, strong typography, Lucide icons |

ExoPlayer is the player inside Media3, not a separate engine. "Product runtime" means Media3/ExoPlayer; libmpv is kept as a reference implementation and benchmark harness, not as the primary user-facing path.

## 2. Engine Strategy

The app focuses on Media3 because it is the Android-native runtime that best fits product UX, platform integration, and long-term maintainability. Shader work should start from proven mpv user shaders when useful, but the finished feature belongs in Media3.

- **Media3 (ExoPlayer)** is the product engine. It owns playback UX, user-facing upscale presets, adaptive control, debug stats, and release-readiness work. mpv shader ideas are ported here through `setVideoEffects()` and custom `GlEffect` / `GlShaderProgram` code.
- **libmpv** is the developer/reference engine. It can load upstream `.glsl` shaders out of the box, generate reference captures, compare quality and performance, and validate whether a Media3 port matches the expected mpv output. It should not drive product architecture or settings UX unless there is an explicit debug build need.

A shared `PlayerEngine` interface remains useful for tests and tooling, but the normal Android app should feel like a single Media3 player rather than a two-engine product.

## 3. Module Layout

```text
gradle/libs.versions.toml   version catalog
build-logic/                convention plugins for KMP, Compose, Android, flavors

composeApp/                 app UI, navigation, platform entry points
  src/commonMain            Compose UI, Decompose components, Koin wiring, Coil
  src/androidMain           Android Activity and flavors; depends on the product Media3 engine
  src/desktopMain           JVM UI sandbox; depends on engine-fake
  src/iosMain               Future target after Android Media3 work stabilizes

core/                       KMP logic libraries without UI
  core-player/              PlayerEngine, PlayerState, tracks, controller, DebugStats
  core-upscale/             UpscaleProfile/Preset, built-ins, import/export, AdaptiveController
  core-detect/              ContentClassifier for anime/cartoon/live-action
  core-media/               local library model, metadata, scanner
  core-settings/            preset, theme, debug, and app settings

engine/                     PlayerEngine implementations and reference tools
  engine-media3/            Android product engine: Media3/ExoPlayer + GLSL ES effects
  engine-mpv/               Developer/reference libmpv path for shader parity, capture, and comparisons
  engine-fake/              FakePlayerEngine for desktop and tests

iosApp/                     Future wrapper after the Android product runtime stabilizes
```

Core logic lives in `core/*`; UI, navigation, and entry points live in `composeApp`; each engine is isolated under `engine/*` and depends only on `:core:core-player`. `composeApp` should wire Media3 for product builds, fake for desktop/tests, and libmpv only for developer/reference workflows.

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

UI depends only on `PlayerEngine` and `PlayerState`. Product builds should normally bind that interface to Media3. `applyUpscale()` is the product application point for Media3 `GlEffect` chains, while libmpv can use the same preset metadata in benchmark/reference tooling to load upstream `.glsl` chains.

## 5. Adaptive Upscaling

`AdaptiveController` decides the active `UpscaleProfile` from device class, thermal status, battery/power-saving mode, actual FPS, and dropped frames. It can move to heavier presets when there is headroom, degrade to lighter Anime4K modes when the device heats up or drops frames, and disable processing under critical thermal pressure.

## 6. Upscale Shader Families

Sakuro treats upscale presets as product-level intent, with Media3 as the final
runtime. libmpv is useful because it can run many upstream shaders unchanged,
so it becomes the reference path for comparing output, finding good candidates,
and building parity tests before porting the winning shader family into Media3.
Anime4K is the current Media3 baseline, but the preset model should leave room
for other shader families instead of baking Anime4K into every upscale path.

| Family | Best fit | Media3 product path | libmpv reference path | License / source | Notes |
|---|---|---|---|---|---|
| Anime4K | Anime and cartoon line art | Current custom parser/runtime baseline | Load upstream `.glsl` unchanged for parity captures | MIT, <https://github.com/bloc97/Anime4K> | Current bundled baseline. Strong for drawn content, but it can look artificial on live-action material. |
| ArtCNN | Anime and cartoon super-resolution | Next likely port candidate; needs dedicated Media3 implementation or parser/runtime expansion | Run upstream mpv GLSL first to choose variants and capture expected output | MIT, <https://github.com/Artoriuz/ArtCNN> | Best first alternative to Anime4K. It provides mpv GLSL shaders aimed at real-time anime playback, with fast and quality variants. |
| FSRCNN / FSRCNNX | General 2x reconstruction and compressed sources | Experimental port after FP16/render-target feasibility checks | Use as reference first because it needs FP16 render targets such as `rgba16f`/`rgba16hf` | GPL-3.0/MIT mix, <https://github.com/igv/FSRCNN-TensorFlow> | Good general-purpose CNN candidate, but license and mobile GPU costs need review before product bundling. |
| RAVU / NNEDI3 prescalers | General sharp luma prescaling | Later Media3 port if general-content results beat simpler scalers | Mature mpv user shaders for reference comparisons | LGPL-3.0, <https://github.com/bjin/mpv-prescalers> | Useful for non-anime content, but LGPL distribution obligations must be handled deliberately. |
| CfL chroma-from-luma | Chroma upsampling for 4:2:0 video | Possible complementary Media3 pass | Pair with reference luma scalers in mpv comparisons | MIT, <https://github.com/Artoriuz/glsl-chroma-from-luma-prediction> | Complementary pass, not a full-frame luma upscaler. It can be paired with another luma scaler. |
| Spatial fast scalers such as GSR / NIS / FSR-style passes | Low-cost mobile-friendly upscale and sharpening | Strong candidate for an early single-pass Media3 port | Use mpv for visual baselines where an implementation exists | Source varies by implementation | Good fallback class for weaker devices. These are less content-aware than CNN shaders but much easier to run in real time. |

Recommended rollout:

1. Add an explicit shader-family selector to built-in profiles, for example
   `AUTO`, `ANIME4K`, `ARTCNN`, `FSRCNNX`, `RAVU`, and `SPATIAL_FAST`, while
   keeping the existing `UpscalePass` list for target scale, sharpening, and
   denoise intent.
2. Generalize benchmark/reference tooling so libmpv can load versioned shader
   bundles from multiple families, each with its own license notice and asset
   version.
3. Use libmpv to establish reference images, performance envelopes, and visual
   failure cases before any Media3 port is accepted.
4. Port ArtCNN into Media3 first if its reference results beat Anime4K enough to
   justify the runtime work. It is the closest conceptual replacement and has
   the cleanest licensing story among the main alternatives.
5. Evaluate FSRCNNX, RAVU, and spatial fast scalers through libmpv-backed
   comparisons, then port only the variants that are viable on mobile GPUs.
6. Treat arbitrary mpv user-shader compatibility as a research tool, not a
   product promise. The current Media3 runtime supports only the subset of mpv
   user-shader directives needed by the bundled Anime4K shaders.

## 7. Flavors And F-Droid

The Android app has two product flavors:

- **`foss`** for F-Droid and normal product use, with no libmpv, Firebase, Crashlytics, Play Services, AdMob, or proprietary SDKs.
- **`full`** for developer/reference benchmarking; it adds the prebuilt libmpv AAR and mpv-specific UI integration.

F-Droid requirements are designed in from day one: FLOSS toolchain, reproducible build, dependency separation, and Media3-only product packaging without libmpv/FFmpeg native libraries.

## 8. Licensing

- libmpv linking makes any shipped app artifact that includes it GPL.
- Anime4K and ArtCNN are MIT; each vendored shader family must keep its original notice under `licenses/`.
- Media3 is Apache 2.0.
- FSRCNNX, RAVU, NNEDI3, and other community shader families require a license review before bundling.

The repository is currently GPLv3 because libmpv is present. If libmpv becomes dev-only and is excluded from product artifacts, licensing can be revisited, but that requires a deliberate dependency and distribution review.

## 9. Monetization

Supported options are donations, GitHub Sponsors, Open Collective, Liberapay, or an optional paid support/pro Play Store listing. Feature paywalls are intentionally avoided because this is FOSS.

## 10. Phases

- **Phase 1:** Android, local media only, Media3 product playback, current Anime4K Media3 runtime, premium Compose UI, `foss`/`full` flavors, F-Droid-ready packaging, desktop UI sandbox with `FakePlayerEngine`, and libmpv reference tooling for shader comparison.
- **Phase 2:** port selected mpv-proven shader families into Media3, adaptive controller for thermal/battery/FPS behavior, universal presets, and SoC-class optimization.
- **Phase 3:** revisit iOS and non-DRM streaming after the Android Media3 pipeline is mature.

## 11. Open Technical Questions

- Compose Multiplatform iOS stability by Phase 3.
- Keeping libmpv available for developer/reference workflows without coupling it to product packaging or settings UX.
- Media3 upscaling: output `Size` control in a `GlEffect` chain and workarounds for `setVideoEffects()` limitations.
- Portability limits of mpv user-shader directives, FP16 render targets, multi-pass graph planning, and mobile GPU cost inside Media3.
- Settings storage: `multiplatform-settings` versus DataStore on Android plus an iOS equivalent.

## 12. Stack Summary

| Library | Role | Notes |
|---|---|---|
| Kotlin Multiplatform | shared logic and UI | Android now, iOS later |
| Compose Multiplatform | declarative UI | shared across product and sandbox targets |
| Decompose | navigation and component lifecycle | correct back/state/process-death handling |
| Koin | dependency injection | multiplatform |
| Coil 3 | images and previews | thumbnails and covers |
| Material3 Adaptive | adaptive layouts | window size classes and navigation suites |
| detekt + ktlint | code quality and style | convention plugin + CI |
| AndroidX | platform base | Media3, DataStore, lifecycle, core-ktx |
| Media3 | Android product playback engine | `setVideoEffects()` + `GlEffect` |
| libmpv | developer/reference shader engine | GPL if shipped; native `.glsl` support for comparison tooling |

All dependencies in the `foss` flavor must be free software and free of proprietary transitive SDKs.

## 13. Code Quality And Tooling

- detekt runs static analysis through `config/detekt/detekt.yml`.
- ktlint formatting is provided through detekt-formatting or a single dedicated ktlint path; avoid duplicate rule systems.
- Both tools belong in convention plugins and apply consistently to `composeApp` and `core/*`.
- CI blocks merges on detekt and ktlint checks.
- A pre-commit hook may run fast checks on changed files.
- Compose-specific detekt rules should catch common composable naming, state, and modifier mistakes.
- `.editorconfig` is the single style source for ktlint.
