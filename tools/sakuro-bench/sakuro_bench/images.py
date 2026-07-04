"""Загрузка, кадрирование, выравнивание и нормализация кадров.

Скриншоты с устройства могут различаться леттербоксом, UI-оверлеями и лёгким
субпиксельным сдвигом. Перед метрикой все кадры приводятся к общей геометрии:
  1. (опц.) ручной кроп UI       -> crop_rect
  2. авто-обрезка чёрных полей   -> autocrop_letterbox
  3. приведение к размеру опорного кадра
  4. трансляционное выравнивание фазовой корреляцией (align_translation)
  5. (опц.) расширение limited->full range
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from PIL import Image


@dataclass
class Frame:
    """RGB-кадр в uint8 [H, W, 3] плюс имя (метка режима)."""

    name: str
    rgb: np.ndarray  # uint8, HxWx3

    @property
    def size(self) -> tuple[int, int]:
        h, w = self.rgb.shape[:2]
        return w, h

    def luma(self) -> np.ndarray:
        """BT.601 luma в float64 [0..255]."""
        r, g, b = (self.rgb[..., i].astype(np.float64) for i in range(3))
        return 0.299 * r + 0.587 * g + 0.114 * b


def load(path: str, name: str | None = None) -> Frame:
    img = Image.open(path).convert("RGB")
    return Frame(name=name or path, rgb=np.asarray(img, dtype=np.uint8))


def save(frame: Frame, path: str) -> None:
    Image.fromarray(frame.rgb, "RGB").save(path)


def crop_rect(frame: Frame, rect: tuple[int, int, int, int]) -> Frame:
    x, y, w, h = rect
    return Frame(frame.name, frame.rgb[y : y + h, x : x + w])


def autocrop_letterbox(frame: Frame, thresh: int = 16) -> Frame:
    """Отрезает почти-чёрные рамки (леттербокс/пиллербокс) по краям."""
    luma = frame.luma()
    rows = np.where(luma.max(axis=1) > thresh)[0]
    cols = np.where(luma.max(axis=0) > thresh)[0]
    if rows.size == 0 or cols.size == 0:
        return frame
    y0, y1 = int(rows[0]), int(rows[-1]) + 1
    x0, x1 = int(cols[0]), int(cols[-1]) + 1
    return Frame(frame.name, frame.rgb[y0:y1, x0:x1])


def resize_to(frame: Frame, size: tuple[int, int]) -> Frame:
    """Lanczos-ресайз к (w, h)."""
    if frame.size == size:
        return frame
    img = Image.fromarray(frame.rgb, "RGB").resize(size, Image.Resampling.LANCZOS)
    return Frame(frame.name, np.asarray(img, dtype=np.uint8))


def align_translation(ref: Frame, mov: Frame) -> tuple[Frame, tuple[int, int]]:
    """Целочисленное выравнивание `mov` к `ref` фазовой корреляцией.

    Оба кадра должны быть одного размера. Возвращает выровненный кадр и
    сдвиг (dy, dx), применённый к `mov`.
    """
    if ref.size != mov.size:
        mov = resize_to(mov, ref.size)
    a = ref.luma()
    b = mov.luma()
    a = a - a.mean()
    b = b - b.mean()
    fa = np.fft.fft2(a)
    fb = np.fft.fft2(b)
    cross = fa * np.conj(fb)
    cross /= np.abs(cross) + 1e-8
    corr = np.fft.ifft2(cross).real
    dy, dx = np.unravel_index(int(np.argmax(corr)), corr.shape)
    h, w = a.shape
    if dy > h // 2:
        dy -= h
    if dx > w // 2:
        dx -= w
    shifted = np.roll(mov.rgb, shift=(dy, dx), axis=(0, 1))
    return Frame(mov.name, shifted), (int(dy), int(dx))


def expand_range(frame: Frame) -> Frame:
    """Расширяет limited (16..235) в full (0..255). Применять, если скриншот в TV-range."""
    x = frame.rgb.astype(np.float64)
    x = (x - 16.0) * (255.0 / (235.0 - 16.0))
    return Frame(frame.name, np.clip(x, 0, 255).astype(np.uint8))
