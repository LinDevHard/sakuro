"""Ground-truth frames pulled straight from a master video with ffmpeg.

The app captures each mode at fixed timestamps (manifest `captureTimestampsMs`)
into a fixed output surface. Given the high-resolution master of the clip that
was played, we decode the frame a player would show at the same timestamp and
fit it into the same output size. That gives a reference that went through no
engine on the device, so neither Media3 nor mpv gets a head start.
"""
from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass

import numpy as np

from .images import Frame

# ffmpeg's scale filter names for the YUV->RGB matrix.
_MATRIX_NAMES = {
    "bt709": "bt709",
    "bt470bg": "bt601",
    "smpte170m": "bt601",
    "bt601": "bt601",
    "smpte240m": "smpte240m",
    "fcc": "fcc",
    "bt2020nc": "bt2020",
    "bt2020c": "bt2020",
}


@dataclass
class VideoInfo:
    width: int
    height: int
    matrix: str      # ffmpeg scale in_color_matrix
    full_range: bool
    guessed: bool    # matrix was not tagged in the file


def probe(path: str) -> VideoInfo:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height,color_space,color_range",
         "-of", "json", path],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        raise SystemExit(f"ffprobe failed on {path}: {out.stderr.strip()}")
    streams = json.loads(out.stdout).get("streams") or []
    if not streams:
        raise SystemExit(f"{path}: no video stream")
    s = streams[0]
    w, h = int(s["width"]), int(s["height"])
    tagged = _MATRIX_NAMES.get(s.get("color_space", ""))
    # Untagged: guess by resolution like mpv does (HD and up -> BT.709).
    matrix = tagged or ("bt709" if w >= 1280 or h > 576 else "bt601")
    return VideoInfo(w, h, matrix, s.get("color_range") == "pc", tagged is None)


def frame_at(path: str, ts_ms: int, size: tuple[int, int], info: VideoInfo) -> Frame:
    """The frame shown at `ts_ms` (last one with pts <= ts), fitted into `size`.

    Fitting mirrors the player: keep aspect, centre, black bars.
    """
    out_w, out_h = size
    t = ts_ms / 1000.0
    start = max(0.0, t - 2.0)
    in_range = "pc" if info.full_range else "tv"
    vf = (
        f"select='lte(t\\,{t:.6f})',"
        f"scale={out_w}:{out_h}:force_original_aspect_ratio=decrease"
        f":flags=lanczos+accurate_rnd+full_chroma_int"
        f":in_color_matrix={info.matrix}:in_range={in_range}:out_range=pc,"
        f"pad={out_w}:{out_h}:(ow-iw)/2:(oh-ih)/2:black,format=rgb24"
    )
    # Input seek lands on a keyframe at or before `start`; -copyts keeps t absolute
    # so the select filter compares against the real timestamp.
    cmd = [
        "ffmpeg", "-hide_banner", "-loglevel", "error",
        "-ss", f"{start:.6f}", "-copyts", "-t", f"{t - start + 0.5:.6f}", "-i", path,
        "-vf", vf, "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1",
    ]
    proc = subprocess.run(cmd, capture_output=True)
    if proc.returncode != 0:
        raise SystemExit(f"ffmpeg failed extracting {ts_ms} ms from {path}: "
                         f"{proc.stderr.decode(errors='replace').strip()}")
    frame_bytes = out_w * out_h * 3
    n = len(proc.stdout) // frame_bytes
    if n == 0:
        raise SystemExit(f"{path}: no frame at or before {ts_ms} ms")
    last = proc.stdout[(n - 1) * frame_bytes:n * frame_bytes]
    rgb = np.frombuffer(last, dtype=np.uint8).reshape(out_h, out_w, 3).copy()
    return Frame(name="ref", rgb=rgb)
