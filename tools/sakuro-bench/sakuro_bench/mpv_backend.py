"""Offline-рендер апскейл-режимов через headless mpv (VO gpu-next).

Гоняет ТЕ ЖЕ Anime4K user-shaders (v4.0.1), что и engine-mpv в приложении, и в том
же каноническом порядке Clamp→Denoise→Restore→Upscale (см. MpvUpscaleProperties.kt).
Даёт быструю итерацию по mpv-режимам без устройства.

ВАЖНО: это ПРИБЛИЖЕНИЕ engine-mpv, не тождество. Десктопный GPU считает шейдеры в
FP32, мобильный может в FP16 — для CNN это видимая разница. Для ранжирования годится,
абсолютные числа сверять с device-capture. Media3-эффекты здесь не воспроизводятся.

Механика: mpv --vo=gpu-next --force-window рендерит кадр в окно нужного размера
(autofit), lua-скрипт делает screenshot-to-file window и выходит. Требуется дисплей
(на headless-CI без window-сервера работать не будет).
"""
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

# Каталог вендоренных шейдеров приложения.
SHADER_DIR = (Path(__file__).resolve().parents[2].parent
              / "engine/engine-mpv/src/main/assets/anime4k")

# Пресеты: (список шейдеров в каноническом порядке, значение mpv --scale).
# Повторяет ветки buildShaderChain()/ewa_lanczossharp из MpvUpscaleProperties.kt.
PRESETS: dict[str, tuple[list[str], str | None]] = {
    "off": ([], "bilinear"),
    "mpv_ewa": ([], "ewa_lanczossharp"),
    "anime4k_s": (["Anime4K_Clamp_Highlights.glsl",
                   "Anime4K_Restore_CNN_S.glsl",
                   "Anime4K_Upscale_CNN_x2_S.glsl"], "ewa_lanczossharp"),
    "anime4k_m": (["Anime4K_Clamp_Highlights.glsl",
                   "Anime4K_Restore_CNN_M.glsl",
                   "Anime4K_Upscale_CNN_x2_M.glsl"], "ewa_lanczossharp"),
    "anime4k_denoise_m": (["Anime4K_Clamp_Highlights.glsl",
                           "Anime4K_Denoise_Bilateral_Mode.glsl",
                           "Anime4K_Restore_CNN_M.glsl",
                           "Anime4K_Upscale_CNN_x2_M.glsl"], "ewa_lanczossharp"),
}

_LUA = """
local done=false
local function grab()
  if done then return end
  done=true
  mp.commandv("screenshot-to-file", os.getenv("SHOT_OUT"), "window")
  mp.command("quit")
end
mp.observe_property("estimated-display-fps","native",function() mp.add_timeout(0.4,grab) end)
mp.register_event("playback-restart",function() mp.add_timeout(0.6,grab) end)
"""


class MpvError(RuntimeError):
    pass


def ensure_mpv() -> None:
    if shutil.which("mpv") is None:
        raise MpvError("mpv не найден в PATH — offline-mpv бэкенд недоступен "
                       "(`brew install mpv`)")
    for name, (shaders, _) in PRESETS.items():
        for sh in shaders:
            if not (SHADER_DIR / sh).exists():
                raise MpvError(f"шейдер {sh} не найден в {SHADER_DIR}")


def render(low_png: str, out_png: str, target_wh: tuple[int, int], preset: str) -> None:
    if preset not in PRESETS:
        raise MpvError(f"неизвестный пресет '{preset}'. Доступно: {', '.join(PRESETS)}")
    shaders, scale = PRESETS[preset]
    w, h = target_wh
    cmd = ["mpv", "--no-config", "--really-quiet", "--idle=once",
           "--vo=gpu-next", "--force-window=immediate",
           f"--autofit={w}x{h}", "--no-keepaspect-window",
           "--image-display-duration=inf"]
    if shaders:
        joined = ":".join(str(SHADER_DIR / s) for s in shaders)
        cmd.append(f"--glsl-shaders={joined}")
    if scale:
        cmd.append(f"--scale={scale}")
    with tempfile.NamedTemporaryFile("w", suffix=".lua", delete=False) as lf:
        lf.write(_LUA)
        lua = lf.name
    cmd += [f"--script={lua}", low_png]
    try:
        subprocess.run(cmd, env={**_env(out_png)}, capture_output=True, text=True, timeout=60)
    except subprocess.TimeoutExpired as exc:
        raise MpvError(f"mpv-рендер '{preset}' завис (>60с)") from exc
    finally:
        Path(lua).unlink(missing_ok=True)
    if not Path(out_png).exists() or Path(out_png).stat().st_size == 0:
        raise MpvError(f"mpv не отрисовал '{preset}' (нет дисплея / window-сервера?)")


def _env(out_png: str) -> dict:
    import os
    e = dict(os.environ)
    e["SHOT_OUT"] = out_png
    return e
