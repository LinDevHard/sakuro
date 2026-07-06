# Sakuro Key Features

> Project: **Sakuro** (`com.rinwave.sakuro`)
> Date: 2026-07-02
> Status: required product features.
> Related: [ARCHITECTURE.md](ARCHITECTURE.md), [DESIGN.md](DESIGN.md), [BRAND.md](BRAND.md).

This document records the features that should definitely exist in the product and how they map to the architecture.

## 1. Content Type Detection

Sakuro should detect content type automatically and choose a suitable upscale preset: **anime / cartoon / live-action / movie**.

### 1.1 Taxonomy

The categories overlap: a movie is often live-action, but can also be animated. The preferred model uses two axes:

- **Visual style**, which matters most for upscaling: `anime`, `cartoon`, or `live_action`.
- **Source character**, which is an additional preset signal: `film` / `movie` with film grain and 24 fps versus regular video.

The detector should emit the primary style plus flags such as film grain and frame rate. "Movie" is interpreted as live-action plus a film profile unless the taxonomy is revised later.

### 1.2 Detection Approach

- Use a lightweight on-device classifier over sampled frames with temporal smoothing and a confidence threshold.
- Anime/cartoon features are usually separable: flat fills, line art, limited palette, and characteristic edges.
- Cheap heuristics can be used as prefilter or fallback: metadata, file name, genre, resolution/aspect ratio, and film grain.
- Battery budget matters: classification should be sparse and cheap, and should slow down or disable itself under thermal or power-saving pressure.
- The `foss` flavor needs a freely licensed model and portable format such as ONNX or TFLite.

### 1.3 Behavior

- Detection selects the preset and exposes the detected class and confidence.
- Manual override is always available and has priority over automatic selection.
- The chosen class can be pinned per file or folder.
- Class and confidence appear in the debug overlay.

### 1.4 Implementation

- `:core:core-detect` owns the KMP `ContentClassifier` interface and platform implementations.
- It works from frame samples/previews and does not depend on the playback engine.
- It emits `Flow<ContentClass>` for `core-upscale`.

## 2. Presets

A preset is a named set of processing parameters applied during playback.

### 2.1 Preset Contents

- Upscale shader chain: Anime4K modes, ArtCNN variants, Ani4K, and similar options, with Media3 as the product runtime.
- Denoise, deblocking, and sharpening.
- Scaling algorithm and target resolution.
- Optional color, tonemapping, and dithering settings.
- Content-class binding for automatic selection.

### 2.2 Preset Types

- Built-in presets for each content class: anime SD, anime HD, live-action light, off, and similar.
- User presets with create, edit, and save workflows.
- Import/export as a file or text string for FOSS community sharing.

### 2.3 Interaction

- Auto-detection selects a preset by class; users can override or pin the choice.
- `AdaptiveController` may temporarily reduce the active preset under heat or FPS drops without changing the user's saved choice.
- Presets are abstract, but product playback targets Media3. Media3 builds `GlEffect` chains for supported options; libmpv is used as a developer/reference path to load upstream `.glsl` chains, compare output, and guide ports. Unsupported product options degrade or hide.

### 2.4 Implementation

- `:core:core-upscale` owns `UpscaleProfile`, presets, built-ins, user store, and import/export serialization.
- Media3 applies presets through `PlayerEngine.applyUpscale(profile)` in product builds. Reference tools may reuse the same profile metadata to configure libmpv comparisons.

## 3. Player Gestures

The player needs a configurable and adaptive gesture set.

- Single tap toggles controls.
- Double tap left/right seeks by 10 seconds; double tap center toggles play/pause.
- Vertical swipe controls brightness on the left and volume on the right.
- Horizontal swipe seeks with position preview.
- Long press temporarily speeds playback, for example to 2x.
- Pinch changes fit / fill / zoom crop modes.
- Optional downward swipe can enter PiP or minimize.

Requirements: configurable sensitivity, safe interaction with system edge gestures and `WindowInsets`, good behavior across device sizes, and haptics for key actions.

Implementation belongs in `composeApp` through Compose gesture APIs so it is shared across the product runtime and sandbox targets. Desktop equivalents such as mouse wheel and drag are secondary.

## 4. Debug Overlay

The "stats for nerds" overlay serves advanced users and upscale debugging.

It should show engine, decoder, codec, container, source-to-output resolution, display/video FPS, dropped and decoded frames, bitrate, pixel format, color space/HDR, audio details, active upscale preset and shader passes, optional GPU time, detected content class, confidence, thermal status, battery/power-saving mode, memory, CPU/GPU load when available, speed, buffering/cache, and timestamps.

The overlay is toggled through settings or gesture, uses a monospace style, and stays unobtrusive.

Implementation: `PlayerEngine.debugStats: Flow<DebugStats>` in `:core:core-player`; Media3 fills product stats from `AnalyticsListener` and decoder counters, libmpv fills reference stats from mpv properties, and shared modules add content and thermal data.

## 5. Module Impact

| Feature | Location |
|---|---|
| Content auto-detect | `:core:core-detect` with `ContentClassifier -> Flow<ContentClass>` |
| Presets | `:core:core-upscale` with `Preset`, `UpscaleProfile`, store, import/export |
| Gestures | `composeApp` gesture handling |
| Debug overlay | `PlayerEngine.debugStats: Flow<DebugStats>` plus UI overlay |
