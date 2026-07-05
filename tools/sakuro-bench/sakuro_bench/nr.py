"""No-reference quality proxies (numpy, no ML).

They work on a single frame without a reference — the only option when there is no
ground truth (native content). These are RELATIVE descriptors, NOT a calibrated MOS:
  * sharpness  — variance of the Laplacian. Higher = sharper, but oversharpening inflates it.
  * hf_ratio   — fraction of HF energy in the spectrum. Both added detail AND ringing raise it.
  * noise      — Immerkaer noise estimate. Lower is usually better (less grain/dirt).
  * ringing    — halo/overshoot proxy near edges. Lower is better.

None can be optimized in isolation: oversharpening gives high sharpness/hf_ratio
but high ringing. Read them together and cross-check with FR and your eyes.
"""
from __future__ import annotations

import numpy as np

NR_KEYS = ["sharpness", "hf_ratio", "noise", "ringing"]
NR_LABELS = {
    "sharpness": "Sharpness (Lap.var)",
    "hf_ratio": "HF energy",
    "noise": "Noise (σ)",
    "ringing": "Ringing",
}
# For NR, "better" is not clear-cut — set a direction only where it is meaningful.
# None = not ranked (descriptor only).
NR_HIGHER_BETTER: dict[str, bool | None] = {
    "sharpness": None,
    "hf_ratio": None,
    "noise": False,
    "ringing": False,
}


def _laplacian(luma: np.ndarray) -> np.ndarray:
    lap = np.zeros_like(luma)
    lap[1:-1, 1:-1] = (
        -4.0 * luma[1:-1, 1:-1]
        + luma[:-2, 1:-1] + luma[2:, 1:-1]
        + luma[1:-1, :-2] + luma[1:-1, 2:]
    )
    return lap[1:-1, 1:-1]


def _sharpness(luma: np.ndarray) -> float:
    return float(_laplacian(luma).var())


def _hf_ratio(luma: np.ndarray, cutoff: float = 0.25) -> float:
    f = np.fft.fftshift(np.fft.fft2(luma - luma.mean()))
    mag = np.abs(f)
    h, w = luma.shape
    yy, xx = np.ogrid[:h, :w]
    r = np.sqrt(((yy - h / 2) / (h / 2)) ** 2 + ((xx - w / 2) / (w / 2)) ** 2)
    total = mag.sum() + 1e-8
    return float(mag[r > cutoff].sum() / total)


def _noise(luma: np.ndarray) -> float:
    """Immerkaer noise σ estimate (convolution with a 3x3 Laplacian mask)."""
    m = (
        luma[:-2, :-2] - 2 * luma[:-2, 1:-1] + luma[:-2, 2:]
        - 2 * luma[1:-1, :-2] + 4 * luma[1:-1, 1:-1] - 2 * luma[1:-1, 2:]
        + luma[2:, :-2] - 2 * luma[2:, 1:-1] + luma[2:, 2:]
    )
    h, w = luma.shape
    return float(np.sqrt(np.pi / 2.0) / (6.0 * (w - 2) * (h - 2)) * np.abs(m).sum())


def _ringing(luma: np.ndarray) -> float:
    """Overshoot proxy: fraction of pixels that "shoot" past the local 3x3 range."""
    a = luma
    stack = np.stack(
        [a[:-2, :-2], a[:-2, 1:-1], a[:-2, 2:],
         a[1:-1, :-2], a[1:-1, 2:],
         a[2:, :-2], a[2:, 1:-1], a[2:, 2:]]
    )
    lo = stack.min(axis=0)
    hi = stack.max(axis=0)
    center = a[1:-1, 1:-1]
    span = hi - lo + 1e-6
    overshoot = np.maximum(center - hi, lo - center) / span
    return float((overshoot > 0.15).mean())


def compute(luma: np.ndarray) -> dict[str, float]:
    return {
        "sharpness": _sharpness(luma),
        "hf_ratio": _hf_ratio(luma),
        "noise": _noise(luma),
        "ringing": _ringing(luma),
    }
