# Feature-Module Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure Sakuro from one `composeApp` module plus chained `core-*` modules into thin app entry points, self-contained feature modules and a flat core layer, with the dependency rules enforced by the build.

**Architecture:** `composeApp` splits into `:androidApp` and `:desktopApp` (entry points, flavors, packaging) plus `:shared` (composition root: Decompose `RootComponent`, DI assembly, `App()`). UI code moves into `:feature:{library,player,settings,bench}`. Features never depend on each other or on engines; they talk to the root through output callbacks. Shared types move to `:core:model`, persistence to `:core:data`. No `api`/`impl` module pairs: each feature's public surface is fixed by Kotlin explicit API mode instead.

**Tech Stack:** Kotlin 2.1.21, AGP 8.10.1, Compose Multiplatform 1.8.2 (multi-module Compose Resources), Decompose 3.3.0, Koin 4.0.3, Gradle convention plugins in `build-logic`.

**Spec:** This document; the decisions come from the 2026-11-12 architecture review. The resulting structure replaces ARCHITECTURE.md §3 (Task 10 rewrites it).

## Global Constraints

- `lib/media3-usershader` is out of scope: do not edit its sources or build file.
- Behavior must not change: no UX or feature changes. Each task only moves code, renames packages, or adds the contracts this plan names.
- `applicationId` stays `com.rinwave.sakuro`; `versionName`/`versionCode` stay in `gradle.properties`.
- The foss flavor must never resolve `:engine:engine-mpv` or the libmpv AAR (`scripts/verify-fdroid-release.sh` must keep passing).
- Product flavors (`foss`/`full`) exist only in `:androidApp`. No other module may contain flavor source sets.
- The quality gate after every task: `bundle exec fastlane android ci` passes, plus `./gradlew :androidApp:assembleFullDebug :desktopApp:run --dry-run` (before Task 3, use the `:composeApp` equivalents).
- All docs, code, comments and commit messages are in English.
- Allowed module edges (enforced by Task 1, with the allowlist emptied by Task 10):

| Module | May depend on |
|---|---|
| `:core:model` | — |
| `:core:player`, `:core:upscale`, `:core:detect`, `:core:media`, `:core:data` | `:core:model` |
| `:core:designsystem` | — |
| `:core:ui` | `:core:model`, `:core:player`, `:core:designsystem` |
| `:engine:*` | `:core:model`, `:core:player`, `:core:upscale`, `:lib:*` |
| `:feature:*` | `:core:*` |
| `:shared` | `:feature:*`, `:core:*` |
| `:androidApp`, `:desktopApp` | `:shared`, `:engine:*`, `:core:*` |
| `:lib:*` | — |

## Review Focus

- Release reproducibility: moving code to `:androidApp` changes the output path and APK file name (`composeApp-foss-release-unsigned.apk` → `androidApp-foss-release-unsigned.apk`). Every script, the CI workflow and the external F-Droid recipe (`subdir`) must follow. Task 3 pins this with the existing verify scripts.
- Saved state: `RootComponent.Config` is `@Serializable` and restored after process death. Moving or renaming it must keep restore working. Task 10 checks this with "Don't keep activities" enabled.
- Compose Resources: a string key missing in `values-ru`/`values-ja` of its new module falls back to English without any error. Task 5 adds a check script; Tasks 6–9 run it.
- Engine surfaces: Media3 (`PlayerView`) and mpv (`SurfaceView`) render through different paths, and PiP re-prepare depends on them. Task 2 verifies playback, PiP and the bench on a device for both engines.
- Settings → Bench navigation becomes a root-level push. Back from the bench must still return to Settings. Task 8 checks it.

---

### Task 1: Module-graph guard

**Files:**
- Create: `build-logic/src/main/kotlin/sakuro.module-rules.gradle.kts`
- Create: `config/module-graph-allowlist.txt`
- Modify: `build-logic/src/main/kotlin/sakuro.kmp.library.gradle.kts`, `sakuro.android.library.gradle.kts` (apply `sakuro.module-rules`)
- Modify: `composeApp/build.gradle.kts` (apply `sakuro.module-rules`)

**Interfaces:**
- Produces: the root task `checkModuleGraph`, wired into `fastlane android quality`. Allowlist format: one `:from -> :to` edge per line, `#` comments allowed.

- [ ] **Step 1:** Implement `sakuro.module-rules`: register `checkModuleGraph` in each project. The task collects `ProjectDependency` entries from all declarable configurations (`*Implementation`, `*Api`, `*CompileOnly`) and matches each edge against the table in Global Constraints. Path patterns: `:core:model`, `:core:*`, `:engine:*`, `:feature:*`, `:lib:*`, `:shared`, `:androidApp`, `:desktopApp`. It fails on edges that are neither allowed nor in the allowlist, and on allowlist entries that no longer exist, so the allowlist can only shrink.
- [ ] **Step 2:** Run `./gradlew checkModuleGraph`. Expected: FAIL. Copy the reported current edges into `config/module-graph-allowlist.txt`. Expected entries include `:core:core-player -> :core:core-upscale`, `:core:core-settings -> :core:core-player`, `:core:core-detect -> :core:core-upscale` and all `:composeApp -> …` edges.
- [ ] **Step 3:** Run `./gradlew checkModuleGraph`. Expected: PASS. Add a fake edge (`implementation(project(":engine:engine-fake"))` in `core-media`) and confirm FAIL. Then revert it.
- [ ] **Step 4:** Add `checkModuleGraph` to the `quality` lane tasks in `fastlane/Fastfile`. Run the quality gate.
- [ ] **Step 5:** Commit `Add module dependency rules with a shrinking allowlist`.

### Task 2: Engine-agnostic video surfaces

Removes `is Media3PlayerEngine` / `is MpvPlayerEngine` checks and flavor code from UI, so the UI can later live in flavorless library modules.

**Files:**
- Create: `core/core-player/src/androidMain/kotlin/com/rinwave/sakuro/core/player/SurfaceVideoOutput.kt`
- Create: `composeApp/src/commonMain/kotlin/com/rinwave/sakuro/ui/VideoSurfaceRenderer.kt` (moves to `:core:ui` in Task 5)
- Modify: `engine/engine-media3/.../Media3PlayerEngine.kt`, `engine/engine-mpv/.../MpvPlayerEngine.kt` (implement `SurfaceVideoOutput`)
- Modify: `composeApp/src/androidMain/.../ui/VideoSurface.android.kt`, `.../bench/BenchRunner.android.kt`, `SakuroApplication.kt`
- Move: the mpv `SurfaceView` code from `composeApp/src/androidFull/.../ReferenceEngine.androidFull.kt` into `MpvSurfaceRenderer` (same flavor source set)
- Move: `ScaleMode` from `composeApp/.../ui/ScaleMode.kt` to `:core:core-player` (`com.rinwave.sakuro.core.player.ScaleMode`)

**Interfaces:**
- Produces (core-player, androidMain):
  ```kotlin
  interface SurfaceVideoOutput {
      fun attachSurface(holder: SurfaceHolder)
      fun resizeSurface(width: Int, height: Int)
      fun detachSurface()
  }
  ```
- Produces (UI, commonMain):
  ```kotlin
  interface VideoSurfaceRenderer {
      fun canRender(engine: PlayerEngine): Boolean
      @Composable fun Render(engine: PlayerEngine, scaleMode: ScaleMode, modifier: Modifier)
  }
  ```
  The renderer list comes from DI (`List<VideoSurfaceRenderer>`), Media3 first. `VideoSurface` uses the first renderer that `canRender`, otherwise `FakeVideoSurface`.
- `referenceVideoSurface`, `attachReferenceEngineSurface` and `detachReferenceEngineSurface` are deleted. `referenceEngineFactories` stays, and the flavor files additionally return `referenceSurfaceRenderers(): List<VideoSurfaceRenderer>`.

- [ ] **Step 1:** Add `SurfaceVideoOutput`. Implement it in `Media3PlayerEngine` (`player.setVideoSurfaceHolder` / `clearVideoSurfaceHolder`, resize is a no-op) and in `MpvPlayerEngine` (existing `attachSurface(holder.surface)` / `resizeSurface` / `detachSurface`).
- [ ] **Step 2:** In `BenchRunner.android.kt`, replace the `is Media3PlayerEngine` branch and `attachReferenceEngineSurface` with `(engine as? SurfaceVideoOutput)`. The bench keeps the fixed 1920×1080 resize and calls `setExactSeeking(true)` only through a new optional `SurfaceVideoOutput.prepareForCapture()` (default no-op; mpv overrides it).
- [ ] **Step 3:** Add `Media3SurfaceRenderer` (androidMain, current `PlayerView` code) and `MpvSurfaceRenderer` (androidFull). Register both in `SakuroApplication`.
- [ ] **Step 4:** Run the quality gate and `assembleFullDebug`. `grep -rn "MpvPlayerEngine\|Media3PlayerEngine" composeApp/src` may only hit renderer files and `SakuroApplication`.
- [ ] **Step 5:** Device check (Pixel/real device), for both engines: play, seek, preset switch, PiP enter/exit with re-prepare, and one bench run that produces a bundle.
- [ ] **Step 6:** Commit `Route video surfaces through engine-agnostic renderers`.

### Task 3: Split `composeApp` into `:shared`, `:androidApp`, `:desktopApp`

This follows JetBrains' AGP 9 layout (an application module may not apply `kotlin.multiplatform`), so the later AGP 9 upgrade does not need a second split.

**Files:**
- Move: `composeApp/` → `shared/` (`git mv`). It becomes a KMP library, namespace `com.rinwave.sakuro.shared`.
- Create: `androidApp/build.gradle.kts` (`com.android.application` + `org.jetbrains.kotlin.android` + compose compiler). Move here: `android {}` app config from `composeApp/build.gradle.kts` (defaultConfig, flavors, signing, buildTypes, `dependenciesInfo`, packaging, `lint {}`), the `ReleaseArtProfile` disabling, `printReleaseInfo`, `proguard-rules.pro`, `src/androidMain/AndroidManifest.xml` + `res/` → `androidApp/src/main/`, `MainActivity`, `SakuroApplication`, `IncomingVideoIntent`, `PipActionReceiver`, `androidFoss`/`androidFull` → `androidApp/src/foss|full/`, and the `androidFullImplementation(engine-mpv)` dependency (now a plain `fullImplementation`, so the `afterEvaluate` hack is removed).
- Create: `desktopApp/build.gradle.kts` (`kotlin("jvm")` + compose). Move here: `desktopMain/main.kt` and the `compose.desktop {}` block. `packageVersion` is derived from `sakuro.versionName` (major bumped to 1 if 0, per the existing comment).
- Modify: `settings.gradle.kts`, `fastlane/Fastfile`, `scripts/verify-{fdroid,play}-release.sh`, `scripts/verify-release-licenses.sh`, `.github/workflows/android.yml`, `.github/CODEOWNERS`, `.github/PULL_REQUEST_TEMPLATE.md`, `README.md`, `CONTRIBUTING.md`, `docs/fdroid.md`, `docs/releasing.md`, `tools/branding/generate_app_icon.py` (res path).
- Modify: `config/module-graph-allowlist.txt` (rename `:composeApp` edges to `:shared`).

**Interfaces:**
- `:shared` exposes `fun App(root: RootComponent)`, `RootComponent`, `appModule(...)`, and `expect`/`actual` UI bridges unchanged. `:androidApp` and `:desktopApp` depend on `:shared` plus the engines they register.
- Gradle task names change: `:composeApp:assembleFossRelease` → `:androidApp:assembleFossRelease`, `:composeApp:testFossDebugUnitTest` → `:androidApp:testFossDebugUnitTest` + `:shared:testDebugUnitTest`, lint → `:androidApp:lintFossRelease`.

- [ ] **Step 1:** Before moving anything, build the current release (`bundle exec fastlane android fdroid`) and save `unzip -l` plus `aapt2 dump badging` of the APK to the scratch dir as the baseline.
- [ ] **Step 2:** Do the moves and build files. Then run `./gradlew :androidApp:assembleFossDebug :androidApp:assembleFullDebug :desktopApp:compileKotlin`.
- [ ] **Step 3:** Update scripts, CI, fastlane and docs. Run `grep -rn composeApp` over the repo (excluding `build/`, `.claude/`, old plans). Expected: no hits.
- [ ] **Step 4:** Run `bundle exec fastlane android ci`. Compare the new APK's badging (package, versions, permissions, activities) and file list with the baseline. Expected: badging identical; file list identical except `classes*.dex` contents.
- [ ] **Step 5:** Note for the release owner (in `docs/fdroid.md`): the fdroiddata recipe must change `subdir: composeApp` → `subdir: androidApp` and the output APK path in the same release.
- [ ] **Step 6:** Device smoke test: launch, library, play a file, open the app from a VIEW intent (file manager), PiP.
- [ ] **Step 7:** Commit `Split composeApp into shared, androidApp and desktopApp`.

### Task 4: Flat core — `:core:model` and `:core:data`

**Files:**
- Create: `core/core-model/` (KMP, `sakuro.kmp.library`, serialization; package `com.rinwave.sakuro.core.model`)
- Create: `core/core-data/` (KMP, multiplatform-settings, serialization; package `com.rinwave.sakuro.core.data`)
- Delete: `core/core-settings/` (its `SakuroSettings` moves to `:core:data`)
- Modify: `build.gradle.kts` files of all `core-*`, `engine-*`, `:shared` (prefer `implementation`; `api` only where a public signature exposes the type)

**Moves:**

| Type | From | To |
|---|---|---|
| `UpscaleProfile`, `UpscalePass` | core-upscale | core-model |
| `ContentClass` | core-upscale | core-model |
| `ThermalLevel`, `DeviceStatus`, `PlaybackHealth` (`DeviceStatus.kt`) | core-upscale | core-model |
| `DisplaySize`, `DisplayLimitStatus` (split out of `DisplayUpscaleLimit.kt`; the object stays) | core-upscale | core-model |
| `EngineType`, `MediaSource`, `SubtitlePreferences`, `ScaleMode` | core-player | core-model |
| `SakuroSettings` | core-settings | core-data |
| `UserPresetStore`, `PinnedPresetStore`, `PresetStores`, `ProfileCodec` | core-upscale | core-data |
| `PlaybackHistoryStore`, `LibraryPreferencesStore` | core-media | core-data |

`UserShaderStore`/`ShaderInspector` interfaces stay in core-upscale (engine contracts). If a moved store needs core-upscale logic, move that pure function to core-model; never add `core-data -> core-upscale`.

- [ ] **Step 1:** Create both modules, move the files with `git mv`, and move their tests along with them (`UserPresetStoreTest`, `PinnedPresetStoreTest`, `LibraryPreferencesStoreTest`, settings test, etc.).
- [ ] **Step 2:** Rewrite imports across the repo. Run `./gradlew compileKotlinDesktop :androidApp:compileFullDebugKotlin`.
- [ ] **Step 3:** Remove the now-gone edges from `config/module-graph-allowlist.txt` (`core-player -> core-upscale`, `core-detect -> core-upscale`, `core-settings -> …`). Run `checkModuleGraph`. Expected: PASS with no core-to-core entries left in the allowlist.
- [ ] **Step 4:** Persistence keys must not change. Run the existing store tests. Then, on a device that has user presets, pins and history from the previous build, upgrade-install the new build and confirm all three survive.
- [ ] **Step 5:** Run the quality gate. Commit `Flatten core into model and data layers`.

### Task 5: `:core:designsystem` and `:core:ui`, resources check

**Files:**
- Create: `build-logic/src/main/kotlin/sakuro.kmp.compose.library.gradle.kts` (sakuro.kmp.library + compose plugins + `compose.components.resources`; `packageOfResClass = "com.rinwave.sakuro.<module>.resources"`)
- Create: `core/core-designsystem/` with `SakuroTheme`, Inter fonts (+ `licenses/inter` reference), `EclipseLoader`, `ui/util/Format.kt`
- Create: `core/core-ui/` with `Localization.kt` (domain enum labels), `VideoSurfaceRenderer` + `VideoSurface` expect/actual, `FilePicker`, `DiagnosticSharing`, `componentScope()` (from `navigation/ComponentScope.kt`)
- Create: `scripts/check-compose-strings.sh`

**Interfaces:**
- `:core:ui` public API: `componentScope()`, the `label()`/`hint()`/`displayName()` mappers, `formatPercent`/`formatMultiplier` (become public), `VideoSurface(engine, scaleMode, modifier)`, `rememberSubtitleFilePicker`, `rememberShaderFilePicker`, `shareDiagnostics`. Renderer registration: `CompositionLocal LocalVideoSurfaceRenderers`, provided by `:shared` from DI.
- `check-compose-strings.sh`: for every module with `src/commonMain/composeResources`, every `<string name>` in `values/` exists in `values-ru/` and `values-ja/`, and no key is defined in two modules. It exits non-zero and prints `module:key:locale` on failure. It is wired into the `quality` lane.

- [ ] **Step 1:** Write the script and run it on the current `:shared`. Expected: PASS (fix any real gaps it reports in a separate commit first).
- [ ] **Step 2:** Create the modules and move the files. Move the strings used by moved code into the new modules' `strings.xml` in all three locales (keys used by both `:shared` and the new module go to `:core:ui`).
- [ ] **Step 3:** Run the string check, the quality gate and `checkModuleGraph`.
- [ ] **Step 4:** Desktop sandbox screenshot plus a device screenshot in `ru` and `ja` locales: theme, fonts and localized labels unchanged.
- [ ] **Step 5:** Commit `Extract design system and shared UI modules`.

### Tasks 6–9: Feature modules

All four use one recipe; each is its own task and commit, in this order: **6 library → 7 settings → 8 bench → 9 player** (smallest blast radius first).

**Shared recipe:**
- Create `build-logic/.../sakuro.feature.gradle.kts` in Task 6: `sakuro.kmp.compose.library` + `kotlin { explicitApi() }` + dependencies on `:core:designsystem`, `:core:ui`, `:core:model`, decompose, decompose-extensions-compose, koin-core, lucide.
- Module `feature/<name>/`, package `com.rinwave.sakuro.feature.<name>`. Everything is `internal` except the feature's API:
  ```kotlin
  public class <Name>Component internal constructor(...) : ComponentContext
  public fun interface <Name>ComponentFactory {
      public fun create(context: ComponentContext, /* args */, onOutput: (<Name>Output) -> Unit): <Name>Component
  }
  public sealed interface <Name>Output
  @Composable public fun <Name>Screen(component: <Name>Component)
  public val <name>FeatureModule: org.koin.core.module.Module   // registers the factory
  ```
- Callbacks currently passed as lambdas (`onOpenVideo`, `onOpenSettings`, `onFinished`, `onBack`) become `Output` cases. The root maps outputs to navigation.
- Move the feature's tests to `feature/<name>/src/commonTest`, its strings into its own resources, and its `expect`/`actual` bridges into its own source sets.
- Done when: the string check passes, `checkModuleGraph` passes with that feature's `:shared` edges replaced by `:shared -> :feature:<name>`, and the quality gate is green.

**Per-feature specifics:**

| Task | Moves in | Outputs | Device check |
|---|---|---|---|
| 6 library | `LibraryComponent`, `LibraryScreen`, `VideoThumbnail`, `MediaLibraryAccess` | `OpenVideo(uri, title)`, `OpenSettings` | permission prompt, folders, sort persists, thumbnails |
| 7 settings | `SettingsComponent` + 6 tab components (merge the four ≤31-line ones into their tab files as internal classes), all `ui/screens/settings/*` except `BenchScreen` | `Back`, `OpenBench` | every tab, preset editor import/export, shader import |
| 8 bench | `BenchComponent`, `BenchModels`, `BenchScreen`, `BenchRunner` (+ android/desktop actuals) | `Back` | Settings → Bench → back returns to Settings; one bundle exported |
| 9 player | `PlayerComponent`, `PlaybackSessionState`, `IncomingVideo`, `DiagnosticReport`, `ui/screens/{PlayerScreen,PlaybackSessionUi,ProcessingNotice,DebugOverlay}`, `ui/components/{SakuroSeekBar,PlayerTransport,YouTubeSeekOverlay}`, `ui/gestures/*`, `PictureInPicture`, `ImmersiveMode`, `PlayerSystemControls`, `FakeVideoSurface`, `ProcessingStatusLog` | `OpenVideo(uri, title)`, `Finished` | full Task 2 device list + gestures, next/previous in folder, resume position |

- Task 8 moves Bench from the settings child stack to `RootComponent.Config.Bench`.
- Task 9 replaces the `ui.isInPipNow` import in `PlayerComponent` with an injected `PipState` interface (`val isInPip: StateFlow<Boolean>`), so the component does not import UI code.
- Task 9 does not split `PlayerScreen.kt` and does not rename `YouTubeSeekOverlay`. Both go in a follow-up plan inside `:feature:player`.

### Task 10: `:shared` as the composition root

**Files:**
- Modify: `shared/.../navigation/RootComponent.kt`, `shared/.../App.kt`, `shared/.../di/*`
- Delete: `AppDependencies` and the `@Suppress("LongParameterList")` on `appModule`/`RootComponent`
- Modify: `ARCHITECTURE.md` §3 (new module layout, the edge table from Global Constraints, the "no api/impl; explicit API mode" rule, the "add `:feature:x:api` only when another feature must call x" rule), `config/module-graph-allowlist.txt` (must end empty)

**Interfaces:**
- `RootComponent(componentContext, factories: RootFactories)` where `RootFactories` is a data class holding the four `*ComponentFactory` instances, resolved from Koin in `:androidApp`/`:desktopApp`.
- `appModules(platform: Module): List<Module>` = core data module + four feature modules + `platform`, where `platform` comes from each app module (engines, renderers, `MediaLibrary`, `DeviceStatusMonitor`, `ContentClassifier`, `UserShaderStore`, `ShaderInspector`).

- [ ] **Step 1:** Rewire the root to the factories and outputs, and delete `AppDependencies`.
- [ ] **Step 2:** `config/module-graph-allowlist.txt` is empty and `checkModuleGraph` passes.
- [ ] **Step 3:** Process-death check: enable "Don't keep activities", open a video, background, return. Expected: the player restores at the same file.
- [ ] **Step 4:** Update ARCHITECTURE.md. Run the full quality gate plus the Task 3 badging comparison.
- [ ] **Step 5:** Commit `Make shared the composition root and enforce the flat graph`.

## Follow-ups (separate plans)

- AGP 9 / Kotlin 2.3 upgrade: library modules move to `com.android.kotlin.multiplatform.library`, and `org.jetbrains.kotlin.android` is removed (built-in Kotlin). It is a small change after this plan because flavors already live only in `:androidApp`.
- `:feature:player` internals: split `PlayerScreen.kt` (sheets, overlays, `PlayerUiState`) and rename `YouTubeSeekOverlay`.
- `lib/media3-usershader` sub-packages (`parse`/`plan`/`gl`).
- `MediaSessionService` for background playback and media buttons.
