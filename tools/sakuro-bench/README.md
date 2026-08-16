# sakuro-bench

`sakuro-bench` is Sakuro's frame-based benchmark utility for comparing upscale modes.
It is intentionally separate from the Gradle project: it has its own Python
environment, works with images/captures, and produces a self-contained report.

The goal is not to produce one magic "quality score". Upscaling quality is easy
to misread: a mode can look sharper while adding halos, noise, or artificial
texture. `sakuro-bench` therefore reports a panel of full-reference and
no-reference metrics, plus visual diffs and side-by-side images.

## What It Compares

The tool compares named render modes. A "mode" can be:

- a real device screenshot from Sakuro, for example `off.png`, `media3_anime4k.png`,
  `mpv_ewa.png`, or `anime4k_m.png`;
- an ffmpeg baseline scaler in the synthetic test, such as `bilinear`, `bicubic`,
  `lanczos`, or `spline`;
- an offline mpv preset that uses the same vendored Anime4K shaders as
  `engine-mpv`.

The report is mode-oriented: every input image becomes a row in the metrics tables,
a chart entry, and a visual comparison item.

## Scenarios

### 1. In-App Benchmark Bundle (recommended)

Sakuro can benchmark itself. In the app: **Settings → Advanced → Benchmark**,
pick a video, pick what to measure, press Run. Two kinds of subject can be
selected side by side:

- **presets** — their passes and any shader chain they carry;
- **shader chains** — every bundled and imported shader, each measured on its
  own as a one-shader chain. "Sweep all" selects the whole registry, so the
  shaders are enumerated in order without building a preset for each one.

The app then renders the video through every engine × subject combination on a
1920×1080 surface, measures startup/first-frame latency, sustained render FPS,
dropped frames, CPU, memory and thermal state, captures the same frames from
every mode, and packs everything into one zip.

Share that zip to your machine and feed it to the tool as a single file:

```bash
cd tools/sakuro-bench
./sakuro-bench device-bundle --bundle sakuro-bench_Pixel-9a_20260816-004500.zip --out report
```

The report folder gets `index.html` (device info, the performance table, links),
`performance.csv`, and one full quality sub-report per capture point
(`t0/index.html`, `t1/…`) with the usual NR metrics, diffs and 1:1 zoom.

Useful options:

```bash
./sakuro-bench device-bundle --bundle bundle.zip --baseline media3_off
./sakuro-bench device-bundle --bundle bundle.zip --ref-dir masters --out report
```

`--baseline` picks the alignment anchor (by default the `*_off` mode). `--ref-dir`
enables full-reference metrics: put ground-truth frames named `t0.png`, `t1.png`,
… — one per capture point — into that folder. A bundle folder that is already
unpacked works in place of the zip.

This scenario supersedes manual screenshotting: the frames are pixel-stable
across devices (no display cutouts, scaling, or HW-overlay black frames), and
quality and performance land in the same report.

### 2. Device Capture Comparison (manual)

Use this when you need frames the in-app benchmark cannot produce — a specific
scene, another player, or a device where the app cannot run the bench.

Capture several modes on the same frame:

```bash
cd tools/sakuro-bench
./capture-device.sh off caps/
./capture-device.sh media3_anime4k caps/
./capture-device.sh mpv_ewa caps/
./capture-device.sh anime4k_m caps/
```

Run no-reference comparison:

```bash
./sakuro-bench capture-compare --captures caps --out report
```

Run full-reference comparison when you have a high-resolution reference frame:

```bash
./sakuro-bench capture-compare \
  --captures caps \
  --ref master_1080p.png \
  --baseline off \
  --out report
```

Capture rules:

- Use the same video, timestamp, scale mode, display size, and orientation.
- Hide controls and overlays.
- Prefer a paused frame with fine detail and clear edges.
- Avoid frames with subtitles unless subtitle rendering is what you want to test.
- If Android `screencap` returns black, SurfaceView may be using a hardware overlay.
  Enable **Disable HW overlays** in Android Developer Options and capture again.

Useful options:

```bash
./sakuro-bench capture-compare --captures caps --crop x,y,w,h
./sakuro-bench capture-compare --captures caps --no-letterbox
./sakuro-bench capture-compare --captures caps --expand-range
./sakuro-bench capture-compare --captures caps --baseline off
```

`--crop` removes UI or status bars before metrics. By default, near-black
letterbox/pillarbox borders are trimmed. `--expand-range` converts limited-range
captures, approximately 16..235, to full 0..255 when the screenshot pipeline
produced TV-range output.

### 3. Synthetic Baseline Test

The `synth` command is a controlled self-test for baseline scalers. It starts
from a high-resolution master frame, creates a lower-resolution input, upscales
that input with selected ffmpeg scalers, and compares the result back to the
master.

```bash
./sakuro-bench synth \
  --master master_1080p.png \
  --scale 2 \
  --degrade realistic \
  --modes bilinear,bicubic,lanczos,spline \
  --out report
```

This is useful for checking whether the metric stack behaves sensibly, but it
does not exercise Sakuro engines.

`--degrade clean` performs a clean downscale. `--degrade realistic` applies
blur and noise before downscaling to better approximate a low-quality source.

### 4. Offline mpv + Anime4K

The `synth-mpv` command renders through desktop `mpv --vo=gpu-next` using the
same vendored Anime4K shader files that Sakuro ships for `engine-mpv`.

```bash
./sakuro-bench synth-mpv \
  --master master_1080p.png \
  --scale 2 \
  --degrade realistic \
  --modes off,mpv_ewa,anime4k_s,anime4k_m,anime4k_denoise_m \
  --out report
```

Available presets:

- `off` - bilinear scaling, no shader chain.
- `mpv_ewa` - mpv `ewa_lanczossharp` scaler.
- `anime4k_s` - Clamp Highlights, Restore CNN S, Upscale CNN S.
- `anime4k_m` - Clamp Highlights, Restore CNN M, Upscale CNN M.
- `anime4k_denoise_m` - Clamp Highlights, Denoise, Restore CNN M, Upscale CNN M.

This approximates `engine-mpv`, but it is not identical. Desktop mpv usually
runs shader math differently from a phone GPU, and mobile rendering may use
different precision or texture formats. Treat `synth-mpv` as a fast ranking
tool; use real device captures for final numbers.

## How The Pipeline Works

For `device-bundle`, the tool unpacks the zip, reads `manifest.json` (device,
video, capture timestamps, per-mode performance metrics), and turns every capture
point into its own comparison group — the modes of one `t<i>` are exactly the
inputs `capture-compare` would get. Performance metrics come from the manifest,
not from the images.

For `capture-compare`, the tool loads every image in `--captures`. The filename
without extension becomes the mode name. Files named `ref`, `reference`,
`master`, `gt`, or `ground_truth` are treated as reference candidates instead
of modes.

Before metrics are computed, each frame is normalized:

1. Optional manual crop through `--crop x,y,w,h`.
2. Automatic trimming of near-black letterbox or pillarbox borders.
3. Optional limited-to-full range expansion through `--expand-range`.
4. Selection of a baseline grid. The baseline is `--baseline`, or the first mode
   alphabetically if no baseline is given.
5. Integer translational alignment of every mode to the baseline using phase
   correlation.
6. If a reference is provided, it is resized to the baseline size and aligned to
   the same grid.

The alignment step handles tiny screenshot offsets, but it does not correct
perspective changes, different crop choices, UI overlays, different frames, or
different scaling modes. If inputs are not genuinely the same frame, the metrics
will be misleading.

## Metric Types

### Full-Reference Metrics

Full-reference, or FR, metrics compare a rendered/upscaled frame against a
known reference frame at the same output size. They are available when you pass
`--ref` or include a reference-named image in the captures folder.

FR is the strongest mode for controlled upscaling tests:

1. Start from a high-quality master frame.
2. Downscale/degrade it into the player input.
3. Render each upscale mode to the target size.
4. Compare every result to the original master.

Reported FR metrics:

| Metric | Direction | What It Means |
|---|---:|---|
| `VMAF` | Higher is better | Perceptual video quality score from Netflix VMAF. Good for ranking natural detail, but can reward enhancement. |
| `VMAF-NEG` | Higher is better | VMAF negative model. More resistant to artificial sharpening and enhancement tricks. |
| `SSIM` | Higher is better | Structural similarity. Sensitive to local structure, contrast, and luminance. |
| `MS-SSIM` | Higher is better | Multi-scale SSIM. Often more stable than plain SSIM for rescaling. |
| `PSNR-Y` | Higher is better | Luma PSNR in dB. Pixel fidelity metric; useful, but often harsh on perceptually plausible reconstruction. |

VMAF and VMAF-NEG are both shown because the gap is informative. If VMAF rises
but VMAF-NEG does not, the mode may be winning by sharpening or contrast rather
than restoring useful detail.

FR limitations:

- A single frame has no temporal VMAF motion component.
- Pixel metrics can punish CNNs for plausible reconstructed details that do not
  exactly match the reference.
- A bad or unrealistic downscale/degrade pipeline can make the wrong mode look best.
- Reference and capture must represent the same frame and composition.

### No-Reference Metrics

No-reference, or NR, metrics describe a frame without a reference. They are used
when you only have screenshots from real content and no ground-truth master.

Reported NR metrics:

| Metric | Direction | What It Means |
|---|---:|---|
| `sharpness` | Descriptor only | Variance of Laplacian. Higher means sharper edges, but oversharpening also raises it. |
| `hf_ratio` | Descriptor only | High-frequency energy ratio. Detail, grain, ringing, and compression noise can all raise it. |
| `noise` | Lower is usually better | Immerkaer noise estimate from a Laplacian mask. |
| `ringing` | Lower is better | Halo/overshoot proxy near local edges. |

NR metrics are not a calibrated opinion score. They should be read as a shape:

- higher sharpness plus low ringing can be a good sign;
- higher sharpness plus high ringing usually means halos;
- high high-frequency energy plus high noise often means texture/noise boost;
- low noise with very low sharpness may mean the image is simply blurred.

## Reading The Report

The output folder contains:

- `index.html` - self-contained visual report.
- `metrics.csv` - machine-readable table for spreadsheets.
- `metrics.json` - machine-readable structured output.

The HTML report includes:

- VMAF vs VMAF-NEG bars, when FR metrics are available.
- A radar chart normalized across the compared modes.
- Sharpness/ringing scatter for NR interpretation.
- Metric tables with best-in-column highlighting where ranking is meaningful.
- Before/after views, zoom panels, and luma diff heatmaps.

Do not choose a winner from one metric alone. A good upscale mode should usually:

- improve FR scores against a valid reference;
- keep VMAF-NEG close to VMAF;
- avoid large increases in ringing;
- avoid increasing noise unless preserving source grain is the explicit goal;
- look plausible in the visual comparison.

## Practical Evaluation Method

For a serious comparison between Sakuro modes:

1. Pick 5-10 representative clips or frames: anime line art, low-bitrate anime,
   live-action faces, dark scenes, high-contrast edges, grainy film, and already
   clean HD content.
2. For controlled FR, create low-resolution inputs from high-quality masters with
   the same target scale Sakuro will use.
3. For real-world NR, capture the exact same frame from each mode on a real device.
4. Use `off` or a simple scaler as the baseline.
5. Inspect FR metrics, then NR metrics, then visual diffs.
6. Reject modes that win VMAF by adding obvious halos, boosted noise, or fake texture.
7. Record device, engine, preset, source resolution, target resolution, and thermal state.

For product decisions, prefer consistent wins across multiple samples over a
single dramatic win on one frame.

## Installation

System requirements:

- `python3`
- `ffmpeg` with `libvmaf`
- `mpv` for `synth-mpv`
- Android `adb` for `capture-device.sh`

Check ffmpeg:

```bash
ffmpeg -filters | grep libvmaf
```

The launcher creates `.venv` on first run:

```bash
cd tools/sakuro-bench
./sakuro-bench --version
```

Manual environment setup also works:

```bash
cd tools/sakuro-bench
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python -m sakuro_bench --help
```

## Commands

```bash
./sakuro-bench device-bundle --bundle bundle.zip --out report
./sakuro-bench device-bundle --bundle bundle.zip --ref-dir masters --out report
./sakuro-bench capture-compare --captures caps --out report
./sakuro-bench capture-compare --captures caps --ref master_1080p.png --baseline off --out report
./sakuro-bench synth --master master_1080p.png --scale 2 --degrade realistic --out report
./sakuro-bench synth-mpv --master master_1080p.png --scale 2 --degrade realistic --out report
```

## Limitations

- It works on frames, not full video sequences.
- Bundle performance numbers describe one uninterrupted run on one device: a
  thermally throttled phone ranks modes differently from a cold one, so compare
  within a bundle, not across bundles.
- It does not measure playback FPS, dropped frames, power use, or thermals.
- Single-frame VMAF lacks temporal motion features.
- Alignment is integer-only and assumes the same frame geometry.
- NR metrics are relative descriptors, not human opinion scores.
- Offline mpv results are useful for ranking but do not replace device captures.

Use it as an evidence tool: it narrows the question, catches bad shader changes,
and makes visual review more disciplined. It does not replace watching real
motion on the target device.
