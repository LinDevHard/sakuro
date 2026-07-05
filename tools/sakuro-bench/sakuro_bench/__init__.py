"""sakuro-bench — a standalone benchmarking tool for upscale-mode quality.

Measures the quality of frames produced by Sakuro's different upscale modes
(bilinear / mpv scalers / Anime4K / Media3 effects), based on screenshots.

Two metric sources:
  * FR (full-reference) — via ffmpeg libvmaf: VMAF, VMAF-NEG, SSIM, MS-SSIM, PSNR.
    Requires a ground-truth reference. The main scenario is "downscale a 1080p
    master → upscale with a mode → compare against the master".
  * NR (no-reference) — numpy proxies: sharpness, HF energy, noise, ringing.
    No reference needed, works on any screenshot. Relative descriptors,
    NOT a calibrated MOS — cross-check with your eyes.

See README.md for the methodology.
"""

__version__ = "0.1.0"
