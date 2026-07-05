"""Offline rendering of upscale modes via headless mpv (VO gpu-next).

Runs the SAME Anime4K user shaders (v4.0.1) as engine-mpv in the app, in the same
canonical order Clamp→Denoise→Restore→Upscale (see MpvUpscaleProperties.kt).
Enables fast iteration over mpv modes without a device.

IMPORTANT: this is an APPROXIMATION of engine-mpv, not an identity. A desktop GPU
computes shaders in FP32, a mobile one may use FP16 — for a CNN that is a visible
difference. Good enough for ranking; cross-check absolute numbers with a device
capture. Media3 effects are not reproduced here.

Mechanics: mpv --vo=gpu-next --force-window renders the frame into a window of the
needed size (autofit), a lua script does screenshot-to-file window and quits. A
display is required (it will not work on headless CI without a window server).
"""
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

# Directory of the app's vendored shaders.
SHADER_DIR = (Path(__file__).resolve().parents[2].parent
              / "engine/engine-mpv/src/main/assets/anime4k")

# Presets: (list of shaders in canonical order, mpv --scale value).
# Mirrors the buildShaderChain()/ewa_lanczossharp branches from MpvUpscaleProperties.kt.
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
        raise MpvError("mpv not found in PATH — offline-mpv backend unavailable "
                       "(`brew install mpv`)")
    for name, (shaders, _) in PRESETS.items():
        for sh in shaders:
            if not (SHADER_DIR / sh).exists():
                raise MpvError(f"shader {sh} not found in {SHADER_DIR}")


def render(low_png: str, out_png: str, target_wh: tuple[int, int], preset: str) -> None:
    if preset not in PRESETS:
        raise MpvError(f"unknown preset '{preset}'. Available: {', '.join(PRESETS)}")
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
        raise MpvError(f"mpv render '{preset}' hung (>60s)") from exc
    finally:
        Path(lua).unlink(missing_ok=True)
    if not Path(out_png).exists() or Path(out_png).stat().st_size == 0:
        raise MpvError(f"mpv did not render '{preset}' (no display / window server?)")


def _env(out_png: str) -> dict:
    import os
    e = dict(os.environ)
    e["SHOT_OUT"] = out_png
    return e
