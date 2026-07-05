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

### 1. Device Capture Comparison

This is the most important scenario because it measures the real app path:
decoder, engine, GPU precision, shaders, scale mode, and Android display output.

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

### 2. Synthetic Baseline Test

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

### 3. Offline mpv + Anime4K

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
./sakuro-bench capture-compare --captures caps --out report
./sakuro-bench capture-compare --captures caps --ref master_1080p.png --baseline off --out report
./sakuro-bench synth --master master_1080p.png --scale 2 --degrade realistic --out report
./sakuro-bench synth-mpv --master master_1080p.png --scale 2 --degrade realistic --out report
```

## Limitations

- It works on frames, not full video sequences.
- It does not measure playback FPS, dropped frames, power use, or thermals.
- Single-frame VMAF lacks temporal motion features.
- Alignment is integer-only and assumes the same frame geometry.
- NR metrics are relative descriptors, not human opinion scores.
- Offline mpv results are useful for ranking but do not replace device captures.

Use it as an evidence tool: it narrows the question, catches bad shader changes,
and makes visual review more disciplined. It does not replace watching real
motion on the target device.
