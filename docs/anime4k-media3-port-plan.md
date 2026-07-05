# Plan: Port Anime4K CNN To The Media3 Engine

Status: **proposal / implementation plan**. Context: the 2026-07-04 `sakuro-bench` run showed that the Media3 "anime" path based on one-pass unsharp/bilateral processing barely improves quality, while real Anime4K shaders in `engine-mpv` reach much higher VMAF and cleaner line art.

Related: [ARCHITECTURE.md](../ARCHITECTURE.md), `engine/engine-media3`, `engine/engine-mpv`, and `tools/sakuro-bench`.

## 0. Key Discovery

This is **not an ML task**. Anime4K weights are embedded directly in GLSL as `mat4(...)` literals. There is no checkpoint to train or extract. The task is an execution framework: reproduce the mpv/libplacebo multi-pass render graph on top of Media3's GL pipeline.

Each pass body is almost ready GLSL. It mainly needs the mpv environment: samplers such as `<tex>_texOff`, texture sizes, and intermediate textures.

## 1. Anime4K Internals

`Anime4K_Upscale_CNN_x2_S` is a 5-pass graph:

```text
MAIN(RGB) -> conv3x3/4ch -> conv2d_tf -> conv2d_1_tf -> conv2d_2_tf
          -> conv2d_last_tf -> depth-to-space x2 + MAIN residual -> MAIN(2x)
```

Each pass uses directives such as `//!HOOK MAIN`, `//!BIND`, `//!SAVE`, `//!WIDTH`, `//!HEIGHT`, `//!COMPONENTS`, and `//!WHEN`.

Porting details:

- Activation is CReLU: `max(x, 0)` and `max(-x, 0)`.
- Weights are inline in `hook()` as `mat4()`.
- The final pass uses depth-to-space plus a bicubic/bilinear residual from `MAIN`.
- Intermediate `COMPONENTS 4` values can go below zero or above one, so float/half render targets are required.
- Depth-to-space uses multi-bind input: `MAIN` plus `conv2d_last_tf`.
- M models are wider/deeper; Restore, Clamp, and Denoise use the same user-shader format.

## 2. Architecture

- Implement a single `Anime4KGlEffect : GlEffect` with an internal render-graph runtime and private FBOs.
- Media3 sees one effect; multi-pass execution is hidden inside it, avoiding Media3 resolution-negotiation issues between many effects.
- Build a generic mpv user-shader runtime instead of hardcoding each shader. Then current and future `.glsl` files can run without per-shader code changes.
- The anime branch does not need a separate `Presentation` upscale because depth-to-space performs 2x scaling.

## 3. Components

1. Directive parser for `HOOK`, `BIND`, `SAVE`, `WIDTH`, `HEIGHT`, `COMPONENTS`, and `WHEN`.
2. RPN evaluator for formulas such as `conv2d_last_tf.w 2 *`.
3. Texture/FBO manager for named intermediates, ping-pong reuse, `GL_RGBA16F`, and pass-size computation.
4. Generated preamble for every bind: `sampler2D <name>_tex`, `<name>_texOff(vec2)`, `<name>_pt`, and `<name>_size`.
5. Pass executor: bind inputs, set viewport, draw fullscreen quad into the `SAVE` target, and gate by `WHEN`.
6. Preset chain builder that reuses mpv chain order: Clamp -> Denoise -> Restore -> Upscale, with S/M model selection.

## 4. Media3 Integration

`Anime4KGlEffect` creates an internal `GlShaderProgram` whose `drawFrame` runs the whole graph and returns the final texture. It owns multi-bind samplers and intermediate FBOs itself. `Media3PlayerEngine` should use this path for ANIME/CARTOON profiles and keep the legacy Sharpen/Denoise/Presentation path for other content.

## 5. Device Risk: FP16

The port requires GLES 3.0 plus color-renderable FP16. Some budget Mali/Adreno devices may lack support or perform poorly. Fallback should be S models only or the existing Media3 sharpen path. Gate this through `AdaptiveController`.

## 6. Performance Risk

The full graph is several fullscreen render-to-texture passes per frame. S is 5 passes; M is heavier. Real-device profiling is required because emulators render on host GPU and are not representative.

## 7. Validation

Use `sakuro-bench` as the oracle. Compare one frame processed by offline mpv with one frame processed by the Media3 port. The implementation delta should be much smaller than the visible difference between modes. `synth-mpv` provides reference frames.

## 8. Rough Phases

| Phase | Scope | Estimate |
|---|---|---|
| 0 | One conv3x3 pass in Media3 with FP16 FBO, compared with mpv | 1-2 days |
| 1 | Directive parser, FBO manager, generated preamble | 3-5 days |
| 2 | Full `Upscale_CNN_x2_S` graph | 2-3 days |
| 3 | Remaining shaders: Restore S/M, Upscale M, Clamp, Denoise | 2-3 days |
| 4 | Preset chain and `Anime4KGlEffect` integration | 2-3 days |
| 5 | FP16/performance detection, fallback, adaptive gating | 2-4 days |
| 6 | Validation, calibration, tests | 2-3 days |

Total: roughly **2.5-4 weeks** for one developer.

## 9. Stop Criteria

- FP16 is unsupported or too slow on target GPUs.
- Multi-pass performance cannot sustain practical playback.
- The Media3-vs-mpv quality delta remains large because of precision or pipeline differences.

## 10. Recommendation

The port is technically straightforward because weights and shader bodies already exist, but it duplicates what `engine-mpv` already does well. First consider engine steering: when content is anime, the UI and adaptive controller can recommend `engine-mpv` and deliver the same result with no shader port.

The Media3 port is justified only if Anime4K is required specifically on Media3. The lowest-risk first step is Phase 0: one convolution pass plus `sakuro-bench` comparison.
