"""No-reference прокси качества (numpy, без ML).

Работают на одиночном кадре без эталона — единственный вариант, когда ground-truth
нет (нативный контент). Это ОТНОСИТЕЛЬНЫЕ дескрипторы, а НЕ калиброванный MOS:
  * sharpness  — дисперсия лапласиана. Выше = резче, но перешарп её раздувает.
  * hf_ratio   — доля ВЧ-энергии в спектре. Рост детализации И ringing поднимают её.
  * noise      — оценка шума по Immerkaer. Ниже обычно лучше (меньше зерна/грязи).
  * ringing    — прокси гало/овершута у краёв. Ниже лучше.

Ни одну нельзя оптимизировать в одиночку: перешарп даёт высокий sharpness/hf_ratio,
но высокий ringing. Смотреть в связке и сверять с FR и глазами.
"""
from __future__ import annotations

import numpy as np

NR_KEYS = ["sharpness", "hf_ratio", "noise", "ringing"]
NR_LABELS = {
    "sharpness": "Резкость (Lap.var)",
    "hf_ratio": "ВЧ-энергия",
    "noise": "Шум (σ)",
    "ringing": "Ringing",
}
# Для NR «лучше» не однозначно — направление задаём только там, где оно осмысленно.
# None = не ранжируем (просто дескриптор).
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
    """Оценка σ шума по Immerkaer (свёртка с лаплас-маской 3x3)."""
    m = (
        luma[:-2, :-2] - 2 * luma[:-2, 1:-1] + luma[:-2, 2:]
        - 2 * luma[1:-1, :-2] + 4 * luma[1:-1, 1:-1] - 2 * luma[1:-1, 2:]
        + luma[2:, :-2] - 2 * luma[2:, 1:-1] + luma[2:, 2:]
    )
    h, w = luma.shape
    return float(np.sqrt(np.pi / 2.0) / (6.0 * (w - 2) * (h - 2)) * np.abs(m).sum())


def _ringing(luma: np.ndarray) -> float:
    """Прокси овершута: доля пикселей, «выстреливающих» за локальный диапазон 3x3."""
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
