# Sakuro Research: Real-Time Video Upscaling

> Project: **Sakuro** (`com.rinwave.sakuro`)
> Date: 2026-07-02
> Status: market and feasibility research.
> Goal: a mobile video player whose key feature is real-time video upscaling.
> Direction is recorded in [ARCHITECTURE.md](ARCHITECTURE.md), [DESIGN.md](DESIGN.md), [FEATURES.md](FEATURES.md), and [BRAND.md](BRAND.md).

Decision summary: open source under GPL; releases through Google Play and F-Droid; KMP + Compose Multiplatform with Decompose, Koin, Coil 3, Gradle convention plugins, and AndroidX; two engines, libmpv and Media3/ExoPlayer, under one UI; Android + Desktop UI sandbox + later iOS; `foss` and `full` flavors with F-Droid compatibility from day one; local media first; premium design with Lucide icons; adaptive layouts and code-quality gates from the start; monetization through donations and optional paid Play support build, not feature paywalls.

## 1. TL;DR

- Upscaling technology is not the moat. The best real-time options are either open-source GLSL shaders or hardware/SoC features such as NVIDIA RTX VSR, AMD upscalers, MediaTek AI-SR, and Arm NSS.
- Value is the product around upscaling: "turn it on and it works", hardware adaptation, thermal/battery behavior, and a platform niche where native solutions are missing.
- Desktop is mostly covered by VLC + RTX VSR and mpv + ArtCNN. Mobile still lacks a polished mass-market upscaling player, but the window is narrow as SoCs add native upscalers.
- Anime4K is dated as a standalone technology; community direction has moved toward ArtCNN and Ani4K.
- On Android, Media3 Effects API is a sensible base: Apache 2.0, ready decoding/streaming, and native UX. Shaders still need manual GLSL ES porting and do not transfer to iOS.

## 2. Upscaling Market Layers

### 2.1 Real-Time GLSL Shaders

Anime4K is an open-source set of real-time anime upscale/denoise algorithms for mpv, IINA, VLC, and Magpie. It is not an AI product by itself; it is shader technology.

Community successors:

- **ArtCNN** is better trained for anime and actively used for luma doubling, especially HD content.
- **Ani4K** is strong on SD and poor WEB/Blu-ray rips because it aggressively removes compression artifacts while preserving style.

MPV Anime Build v4.3 combines anime/live-action detection and adaptive upscaling with ArtCNN, Anime4K, NNEDI3, and FSRCNNX. These run on ordinary GPUs through GLSL without transcoding and are the main free competitor.

### 2.2 Hardware Upscaling

- **NVIDIA RTX VSR** uses RTX tensor cores and works in VLC, PotPlayer, Chromium, Chrome, and Edge.
- **AMD VSR** is weaker spatial upscaling for RX GPUs.
- **MediaTek AI-SR** provides efficient real-time upscaling in TV SoCs.
- **Arm Neural Super Sampling** brings DLSS-like neural upscaling to Mali GPUs, with broad rollout expected around late 2026.

The trend is clear: upscaling becomes a platform/hardware feature rather than an app feature.

### 2.3 Offline AI Upscaling

Video2X, Upscayl, Waifu2x Extension GUI, Topaz Video AI, and UniFab target restoration and transcoding. They produce higher quality but are not real-time player competitors.

## 3. Mobile Focus

Current mobile competitors are weak. Anime4K is not smooth out of the box on phones; VLC and mpv-android support shaders technically but require manual configuration and are not a mass-market experience. There is no widely polished official player with built-in real-time upscaling in the stores.

Real-time mobile upscaling is technically plausible. Research shows video super-resolution can reach real-time FPS on mobile GPU/NPU hardware; Apple MetalFX, Qualcomm AI Frame Fusion, MediaTek AI-SR, and Arm NSS all point in the same direction. However, game upscalers and video post-decode upscalers have different pipelines, especially around motion vectors.

Main mobile risks:

1. Thermal and battery cost can ruin UX during full-screen 4x upscaling.
2. iOS energy rules demand strong optimization.
3. Android fragmentation requires fallbacks on weaker SoCs.
4. On a 6-inch screen, visible gains are strongest for SD, low-bitrate, and anime content; high-quality 1080p often sees little improvement.

## 4. Viable Niches

Direct competition with VLC, mpv, and RTX is weak positioning. Better entrances:

- Mobile platforms without native RTX-style upscaling.
- Anime, retro, and low-bitrate communities that want one-tap quality.
- B2B embedding for streaming, surveillance, or TV boxes.
- An all-in-one player with upscale, denoise, frame interpolation, content detection, and adaptive presets.

## 5. Android Feasibility With Media3

Media3 supports playback effects:

```kotlin
exoPlayer.setVideoEffects(listOf(pass1, pass2, ...))
```

Custom `GlShaderProgram` + `GlEffect` can implement shader passes. Real upscaling is possible because `configure()` can return a larger output `Size`, not just a filtered same-size frame. Multi-pass Anime4K/ArtCNN can be modeled as a chain of effects, and the adaptive controller can change the list at runtime.

Pitfalls:

1. mpv user shaders do not transfer directly. `//!HOOK MAIN`, `HOOKED`, `LUMA`, and related directives must be adapted to GLSL ES and Media3's texture model.
2. DRM/protected content is a hard stop. Secure decoders render to protected surfaces where effects cannot access decoded frames.
3. `setVideoEffects()` still has rough edges and bugs around black screens or crashes in some layouts/effects.

Media3 is the better Android-first base; libmpv is better for shared shader reuse and cross-platform engine strategy, but brings GPL and native integration cost.

## 6. Architecture Direction

```text
Native shell
  Android: Kotlin/Compose
  iOS: SwiftUI or shared Compose later

Upscale core
  Decode: Media3/ExoPlayer on Android, AVPlayer or libmpv on iOS/cross-platform
  Backend abstraction: GLSL ES on Android, Metal on iOS
  Engines: Anime4K/ArtCNN shaders plus optional NN model
  Adaptive controller: thermal, battery, FPS, and device class
```

The adaptive controller is the product core: it chooses a preset for the SoC, thermal state, battery, and real FPS; degrades gracefully; and disables processing when needed.

## 7. Recommended Phasing

- **Phase 1:** Android, local files, anime/SD presets, Media3, GLSL ES ports, and a premium UI. This tests whether users value convenient phone upscaling.
- **Phase 2:** adaptive controller and optional NN model for broader content on stronger SoCs.
- **Phase 3:** iOS shell and streaming, then decide between Metal ports and shared libmpv.

Open questions:

1. Streaming source: own/open streams only, because DRM services do not allow decoded-frame access.
2. Monetization: subscription, one-time purchase, or freemium; avoid hard feature paywalls for FOSS.

## 8. Sources

- Anime4K: https://github.com/bloc97/Anime4K
- ArtCNN vs Anime4K discussion: https://github.com/dyphire/mpv-config/discussions/78
- MPV Anime Build: https://chinna95p.github.io/mpv-anime-build/
- Open-source video upscalers: https://www.aiarty.com/ai-video-enhancer/open-source-video-upscaler-enhancer.htm
- NVIDIA RTX VSR: https://blogs.nvidia.com/blog/rtx-video-super-resolution/
- AMD Video Upscaler: https://www.tomshardware.com/pc-components/gpus/amd-video-upscaler-arrives-for-rx-7000-gpu-owners-yearning-for-nvidias-rtx-video
- MediaTek AI Super Resolution: https://www.mediatek.com/press-room/mediatek-ai-super-resolution-to-improve-streaming-content-on-hisense-smart-tvs
- Anime4K on Android issue: https://github.com/bloc97/Anime4K/issues/99
- Power Efficient Video SR on Mobile: https://arxiv.org/pdf/2211.05256
- Real-Time Video SR on Smartphones: https://arxiv.org/pdf/2105.08826
- Arm Neural Super Sampling: https://newsroom.arm.com/news/arm-announces-arm-neural-technology
