# sakuro-bench

An independent frame-based benchmarking tool for Sakuro upscale modes. It is not coupled to the Gradle project and owns its own Python virtual environment.

## Why

Sakuro can upscale video through different modes: bilinear, mpv scalers such as `ewa_lanczossharp`, Anime4K CNN, and custom Media3 effects. This tool answers "which mode is objectively better for this content?" through metrics instead of eyeballing.

## Methodology

Full-reference metrics need a reference at the same resolution. For upscaling, there is no perfect live reference, so the tool supports two paths:

- **FR through downscale from a master.** Start from a high-quality original, downscale it to the player input, upscale it with a mode, then compare with the original. Match the scale factor to the real target, and use realistic degradation such as blur plus noise.
- **NR without reference.** When the source is already the minimum resolution, use no-reference proxies: sharpness, high-frequency energy, noise, and ringing.

Read metrics as a panel, not one number. Pixel FR metrics can punish CNNs for reconstructed detail and sharpening. VMAF-NEG helps reveal when a gain is only sharpening.

## Installation

No manual setup is needed. The launcher creates `.venv` on first run. System requirements: **ffmpeg with libvmaf** (`ffmpeg -filters | grep vmaf`) and **python3**.

```bash
cd tools/sakuro-bench
./sakuro-bench --version
```

## Scenario 1: Real Device Captures

This is the most trustworthy path because it measures the real pipeline: engine, GPU, and FP16 behavior.

```bash
# 1. Pause Sakuro on the same frame, choose a mode, hide controls, capture each mode.
./capture-device.sh off       caps/
./capture-device.sh mpv_ewa   caps/
./capture-device.sh anime4k_m caps/

# 2a. NR only, no reference.
./sakuro-bench capture-compare --captures caps --out report

# 2b. Strict FR: play a 480p source derived from master_1080p.png.
./sakuro-bench capture-compare --captures caps --ref master_1080p.png --out report
```

Capture requirements: same frame, same timecode, same scale mode, no UI. The tool can crop, align, and normalize with `--crop x,y,w,h`, `--no-letterbox`, and `--expand-range`.

If `screencap` returns black, SurfaceView likely moved to a hardware overlay. Enable **Disable HW overlays** in Android Developer Options.

## Scenario 2: Synthetic Self-Test

Quickly verify metric and baseline-scaler behavior without a device:

```bash
./sakuro-bench synth --master master_1080p.png --scale 2 \
    --degrade realistic --modes bilinear,bicubic,lanczos,spline --out report
```

## Scenario 3: Offline mpv + Anime4K

`synth-mpv` runs the same Anime4K user shaders as `engine-mpv` through headless-ish `mpv --vo=gpu-next`, in canonical Clamp -> Denoise -> Restore -> Upscale order.

Requires installed `mpv` and a display.

```bash
./sakuro-bench synth-mpv --master master_1080p.png --scale 2 --degrade realistic \
    --modes off,mpv_ewa,anime4k_s,anime4k_m --out report
```

Presets: `off`, `mpv_ewa`, `anime4k_s`, `anime4k_m`, and `anime4k_denoise_m`.

This approximates `engine-mpv` but is not identical. Desktop usually computes CNN passes in FP32, while mobile may use FP16. Use `capture-compare` on device for absolute numbers.

## Report

`report/index.html` is self-contained and includes:

- Inline-SVG infographics: VMAF vs VMAF-NEG bars, quality radar, and sharpness/ringing scatter.
- FR/NR tables with best-in-column highlighting.
- Before/after galleries with draggable sliders and diff heatmaps.

The report also writes `report/metrics.csv` and `report/metrics.json`.

## Metrics

FR, higher is better: `VMAF`, `VMAF-NEG`, `SSIM`, `MS-SSIM`, `PSNR-Y`.

NR, relative: `sharpness`, `hf_ratio`, `noise` where lower is better, and `ringing` where lower is better.

Optional extensions such as BRISQUE, NIQE, MUSIQ, CLIP-IQA, SSIMULACRA2, and LPIPS can be added through a separate backend, but many require PyTorch and Python 3.11/3.12 wheels.
