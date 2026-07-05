"""Full-reference metrics via ffmpeg libvmaf.

A single libvmaf call with features yields VMAF (default model) + PSNR + SSIM + MS-SSIM.
A second call with the vmaf_v0.6.1neg model yields VMAF-NEG (penalizes "cheating" sharpening —
shows whether a mode truly restores detail or gamed the metric through contrast).

Frames are compared pixel by pixel, so dist and ref must be the same size —
the alignment pipeline (images.py) guarantees that before the call.

Note: on a single frame the VMAF motion feature = 0 (no temporal component).
The score is slightly lower but identical across all modes — the ranking stays fair.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

FR_KEYS = ["vmaf", "vmaf_neg", "float_ssim", "float_ms_ssim", "psnr_y"]
# "Better" direction: for every FR metric, higher = better.
FR_HIGHER_BETTER = {k: True for k in FR_KEYS}
FR_LABELS = {
    "vmaf": "VMAF",
    "vmaf_neg": "VMAF-NEG",
    "float_ssim": "SSIM",
    "float_ms_ssim": "MS-SSIM",
    "psnr_y": "PSNR-Y (dB)",
}


class FfmpegError(RuntimeError):
    pass


def ensure_ffmpeg() -> None:
    if shutil.which("ffmpeg") is None:
        raise FfmpegError("ffmpeg not found in PATH")
    out = subprocess.run(
        ["ffmpeg", "-hide_banner", "-filters"], capture_output=True, text=True
    ).stdout
    if "libvmaf" not in out:
        raise FfmpegError("ffmpeg built without libvmaf — FR metrics unavailable")


def _run_libvmaf(dist_png: str, ref_png: str, extra: str) -> dict:
    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
        log = tf.name
    lavfi = (
        "[0:v]format=yuv420p[d];[1:v]format=yuv420p[r];"
        f"[d][r]libvmaf={extra}:log_fmt=json:log_path={log}"
    )
    proc = subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error",
         "-i", dist_png, "-i", ref_png, "-lavfi", lavfi, "-f", "null", "-"],
        capture_output=True, text=True,
    )
    try:
        data = json.loads(Path(log).read_text())
    except (OSError, json.JSONDecodeError) as exc:
        raise FfmpegError(f"libvmaf produced no result: {proc.stderr.strip()}") from exc
    finally:
        Path(log).unlink(missing_ok=True)
    return data["pooled_metrics"]


def compute(dist_rgb: np.ndarray, ref_rgb: np.ndarray) -> dict[str, float]:
    """Computes the FR panel for a pair of aligned same-size RGB frames."""
    with tempfile.TemporaryDirectory() as td:
        dist_png = str(Path(td) / "dist.png")
        ref_png = str(Path(td) / "ref.png")
        Image.fromarray(dist_rgb, "RGB").save(dist_png)
        Image.fromarray(ref_rgb, "RGB").save(ref_png)

        result: dict[str, float] = {}
        # 1) default model + features (VMAF, PSNR, SSIM, MS-SSIM)
        try:
            m = _run_libvmaf(
                dist_png, ref_png,
                "feature='name=psnr|name=float_ssim|name=float_ms_ssim'",
            )
            result["vmaf"] = float(m["vmaf"]["mean"])
            result["float_ssim"] = float(m["float_ssim"]["mean"])
            result["float_ms_ssim"] = float(m["float_ms_ssim"]["mean"])
            result["psnr_y"] = float(m["psnr_y"]["mean"])
        except (FfmpegError, KeyError):
            for k in ("vmaf", "float_ssim", "float_ms_ssim", "psnr_y"):
                result[k] = float("nan")
        # 2) NEG model
        try:
            m = _run_libvmaf(dist_png, ref_png, "model='version=vmaf_v0.6.1neg'")
            result["vmaf_neg"] = float(m["vmaf"]["mean"])
        except (FfmpegError, KeyError):
            result["vmaf_neg"] = float("nan")
    return result
