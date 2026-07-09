# Universal mpv user-shader runtime on Media3 — parity plan

Goal: evolve the Anime4K-specific runtime in `engine-media3` into a **generic library that
runs arbitrary mpv user shaders** (FSRCNNX, ravu, KrigBilateral, AMD FSR/CAS ports, NNEDI3,
Adaptive Sharpen, ArtCNN, …) the way mpv does out of the box — targeting **functional parity
with the mpv/libplacebo `.hook` format**, spec: <https://libplacebo.org/custom-shaders/>
(superset of classic `--vo=gpu`; modern shaders target it).

The Anime4K port (package `engine/engine-media3/.../anime4k/`) already validated the hard
parts on device: multi-pass FP16 graph inside one `BaseGlShaderProgram`, RPN size planning,
`//!WHEN` gating, HOOKED aliasing. VMAF parity with mpv confirmed by sakuro-bench. That
runtime becomes the seed of the generic one.

## Spec coverage: current vs required

| Feature | mpv/libplacebo | Current runtime | Plan phase |
|---|---|---|---|
| `//!DESC/BIND/SAVE/WIDTH/HEIGHT/WHEN/COMPONENTS` | ✓ | ✓ | — |
| `//!HOOK` multiple per pass | up to 16 | first one only | 1 |
| RPN ops | `+ - * / % > < = !` (fuzzy `=`) | no `%`, `!`, strict `=` | 1 |
| Per-bind symbols | `_tex _texOff _raw _pos _size _pt _off _mul _rot _map _gather` | `_tex _texOff _pos _size _pt _mul` | 1 (`_gather` in 5) |
| Globals | `frame random input_size target_size tex_offset linearize() delinearize()` | none | 1 |
| `//!OFFSET <x y \| ALIGN>` | ✓ | ignored | 2 |
| Hook points | `RGB LUMA CHROMA ALPHA XYZ CHROMA_SCALED ALPHA_SCALED NATIVE MAIN MAINPRESUB LINEAR SIGMOID PREKERNEL POSTKERNEL SCALED PREOUTPUT OUTPUT` | `MAIN` (+`PREKERNEL/NATIVE` canonicalized to it) | 2 (frame stages), 3 (planes) |
| `//!TEXTURE` blocks (`SIZE FORMAT FILTER BORDER DATA`) | ✓ | ignored | 4 |
| `//!COMPUTE` (compute shaders, `out_image`) | ✓ | ignored | 5 |
| `//!BUFFER` blocks (UBO/SSBO, `VAR`, `STORAGE`) | ✓ | ignored | 5 |
| `//!PARAM` blocks (tunables, ENUM/DYNAMIC/CONSTANT/DEFINE) | ✓ | ignored | 6 |
| User shader files (not just vendored assets) | `glsl-shaders=` | assets only | 6 |

## Architecture decisions

**Placement.** Phase 1 creates package `engine/engine-media3/.../usershader/` (generic,
no Anime4K knowledge). `anime4k/` shrinks to preset policy (`Anime4KChain`: profile →
`.glsl` files). Extraction into a standalone Gradle module (potentially open-sourceable
`media3-mpv-shaders`) is deferred until the API stabilizes — cheap to do later.

**Stage model.** Media3 hands us one RGB texture after decode; mpv hooks a richer pipeline.
Mapping:

- `MAIN`/`MAINPRESUB`/`NATIVE`/`PREKERNEL` — one evolving frame slot (today's
  canonicalization), since our runtime *is* the scaler and there is no subtitle blending
  inside the video pipeline.
- `LINEAR`/`SIGMOID` — emulated: when a pass hooks them, the planner inserts
  linearize/sigmoidize passes around it (transfer function of the SDR working space;
  Media3 SDR effects see electrical RGB — verified by the Anime4K port).
- `POSTKERNEL`/`SCALED`/`PREOUTPUT`/`OUTPUT` — run at present stage, after the frame slot
  reaches final size. `OUTPUT` size = Media3 output size.
- `LUMA`/`CHROMA`/`RGB`/`ALPHA` (+`_SCALED`) — **virtual planes**: decompose the RGB input
  (BT.709/601 by video colorspace) into a full-res luma texture and a chroma texture, run
  plane-hooked passes at plane resolution, recombine before `MAIN` passes. *Known
  deviation from mpv*: mpv hooks real decoder planes before chroma upsampling; Media3's
  EGL sampling already upscaled chroma. Functionally equivalent for luma-driven shaders
  (FSRCNNX/ravu/ArtCNN hook LUMA); chroma shaders (KrigBilateral) get already-upsampled
  chroma — documented, benchmarked in phase 8.
- `XYZ` — not emulated (no XYZ sources in scope); passes hooking only XYZ are skipped,
  same as mpv skips hooks for absent textures.

**Capability tiers.** ES 3.0 is the floor (FP16 render targets — already probed).
`//!COMPUTE`, `//!BUFFER` SSBO, `textureGather`, `TEXTURE STORAGE` need ES 3.1: probe the
actual context version at `configure()` (Android returns the highest 3.x for
`EGL_CONTEXT_CLIENT_VERSION 3`), emit `#version 310 es` and `NAME_gather` only when
available. A shader needing unavailable features degrades per-chain to passthrough with a
log tag (existing pattern), never breaks playback.

**Execution.** Keep the single-`BaseGlShaderProgram` render-graph design: Media3 sees one
effect; all passes (fragment or compute) run in `drawFrame` on internal FP16 FBOs / images.
`configure()` re-plans on size change; `//!WHEN` with `PARAM`s that change at runtime
re-plans on demand.

## Phases

1. ✅ **Generalize the core** (2026-07-08) — runtime moved to `usershader/`
   (`UserShaderProgram`, `ShaderGraphPlanner`, …); block-based parser returns a
   `ShaderDocument` (passes + TEXTURE/BUFFER/PARAM blocks parsed, execution later);
   multi-`//!HOOK`; full RPN op set; full per-bind symbol set + globals; frame
   counter/random uniforms. Unsupported executor features fail the plan with
   `UserShaderException` → passthrough. Log tag is now `UserShader`.
2. ✅ **Frame-stage graph** (2026-07-08) — full frame-stage hook set with mpv
   pipeline firing order (`NATIVE→MAIN→LINEAR→SIGMOID→PREKERNEL→POST…`), post-scale
   slot presented after MAIN, synthetic linearize/sigmoidize brackets,
   `//!OFFSET`/`ALIGN` accumulation compensated at present, resizability
   enforcement, `NATIVE_CROPPED` pseudo-texture. *Acceptance checked statically:
   adaptive-sharpen and SSimSuperRes parse+plan unmodified (test fixtures in
   `src/test/resources/usershader/`); Anime4K graph verified on emulator
   (854×480→1708×960, no passthrough). Note: Anime4K De-Ring-Clamp now fires last
   (true mpv order) — re-bench in phase 8.*
3. ✅ **Virtual planes** (2026-07-09) — LUMA (full-res Y) and CHROMA (half-res
   CbCr, BT.709 full-range) synthesized on demand from the RGB frame, plane
   hooks fire first and may resize their plane, `CHROMA_SCALED` via a synthetic
   upscale, planes merged back into MAIN at the final LUMA size only when a
   hooked pass wrote a plane in place (no chroma round-trip otherwise); binding
   a plane from any pass forces extraction. *Acceptance: FSRCNNX x2 plans to a
   ×2 frame through the LUMA plane, KrigBilateral plans with chroma upscaled to
   the luma size (fixtures in `src/test/resources/usershader/`); all 28
   generated fragments for the four fixture chains validate as GLSL ES 3.00
   with glslang (see `FragmentDumpTest`). On-device visual check pending a way
   to select these chains in the app (phase 6).*
4. ✅ **`//!TEXTURE`** (2026-07-09) — LUT upload at configure (1D/2D; float and
   unorm formats via `ShaderTextureFormats`, validated data size at plan time),
   FILTER/BORDER mapping, samplers named by the bare bind name (mpv-style:
   `texture(ravu_lut3, …)` works), `texOff` is a macro with `vec2()` conversion
   as in mpv. Classic-mpv `16f` formats carry float32 payload (ravu), `16hf` are
   true halves. 3D and `STORAGE` textures still degrade (phase 5+).
   *Acceptance: ravu-r3 (fragment) parses+plans (×2 through LUMA, OFFSET −0.5)
   and all 35 generated fragments for the five fixture chains validate with
   glslang.*
5. **ES 3.1 tier** — `NAME_gather`, `//!COMPUTE` (`out_image`, workgroups, barriers),
   `//!BUFFER`, `TEXTURE STORAGE`; capability probe + per-chain gating.
   *Acceptance: FSR/CAS with gather path, ravu compute variants, NNEDI3 run on capable devices.*
6. **User-facing** — `//!PARAM` (+ settings UI for tunables), import of arbitrary `.glsl`
   via SAF into `MpvShaderStore` (shared with engine-mpv so both engines eat the same
   files), custom chains in presets (ordered shader list per content class), validation
   errors surfaced in UI instead of silent passthrough.
7. **Perf & adaptivity** — early FP16/3.1 probing, RTT budget per chain,
   `AdaptiveController` degradation (drop passes like mpv never does — our advantage),
   profiling on real hardware.
8. **Validation** — sakuro-bench matrix: each supported shader on Media3 vs the same chain
   on engine-mpv (VMAF/ringing/SSIM), plus on-device visual checks. Parity report kept in
   this doc.

## Risks / open questions

- Media3 working color space for HDR input differs (linear); plane emulation and
  `linearize()` must branch on `useHdr` — HDR shader support is explicitly *after* SDR parity.
- Compute-shader dispatch inside Media3's GL thread is untested on-device (context is
  shared; `glDispatchCompute` + `glMemoryBarrier` should be safe — verify early in phase 5).
- `//!WHEN` referencing `PARAM`s makes plans dynamic; cache plans per (size, param-set).
- Chroma deviation (see stage model) may hurt chroma-only shaders; measure, don't guess.
