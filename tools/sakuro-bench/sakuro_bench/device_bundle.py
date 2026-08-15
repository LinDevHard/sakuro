"""Device benchmark bundles produced by Sakuro's in-app benchmark.

A bundle is a single zip:

    manifest.json
    captures/<mode>/t0.png, t1.png, …

`manifest.json` (schema 1) carries the device, the video, the capture
timestamps and, per mode, the performance metrics measured while that mode was
rendering. Every mode captured the *same* timestamps, so each `t<i>` is one
comparison group — exactly the `capture-compare` scenario, minus the manual
screenshot work.
"""
from __future__ import annotations

import json
import zipfile
from dataclasses import dataclass
from pathlib import Path

from . import report

SCHEMA = 1

# Performance columns, in report order.
PERF_KEYS = ["startup_ms", "first_frame_ms", "render_fps", "dropped_frames",
             "cpu_avg_percent", "cpu_peak_percent", "rss_peak_mb",
             "thermal_status", "thermal_headroom", "battery_temp_c"]

PERF_LABELS = {
    "startup_ms": "startup, ms",
    "first_frame_ms": "first frame, ms",
    "render_fps": "render FPS",
    "dropped_frames": "dropped",
    "cpu_avg_percent": "CPU avg, %",
    "cpu_peak_percent": "CPU peak, %",
    "rss_peak_mb": "RSS peak, MB",
    "thermal_status": "thermal",
    "thermal_headroom": "headroom",
    "battery_temp_c": "battery, °C",
}

# Only the unambiguous ones are ranked: FPS up, startup/latency/drops/CPU down.
PERF_HIGHER_BETTER = {
    "startup_ms": False,
    "first_frame_ms": False,
    "render_fps": True,
    "dropped_frames": False,
    "cpu_avg_percent": False,
    "cpu_peak_percent": False,
    "rss_peak_mb": False,
    "thermal_status": None,
    "thermal_headroom": None,
    "battery_temp_c": None,
}

# manifest key -> report key
_METRIC_KEYS = {
    "startupMs": "startup_ms",
    "firstFrameMs": "first_frame_ms",
    "renderFps": "render_fps",
    "droppedFrames": "dropped_frames",
    "cpuAvgPercent": "cpu_avg_percent",
    "cpuPeakPercent": "cpu_peak_percent",
    "rssPeakMb": "rss_peak_mb",
    "thermalStatus": "thermal_status",
    "thermalHeadroom": "thermal_headroom",
    "batteryTempC": "battery_temp_c",
}


class BundleError(RuntimeError):
    pass


@dataclass
class Bundle:
    root: Path
    manifest: dict

    @property
    def modes(self) -> list[str]:
        return [m["mode"] for m in self.manifest["modes"]]

    @property
    def timestamps(self) -> list[int]:
        return list(self.manifest.get("captureTimestampsMs", []))

    def perf_rows(self) -> dict[str, dict[str, float]]:
        """Performance metrics per mode; missing values become nan."""
        rows: dict[str, dict[str, float]] = {}
        for entry in self.manifest["modes"]:
            metrics = entry.get("metrics", {}) or {}
            rows[entry["mode"]] = {
                report_key: _as_float(metrics.get(manifest_key))
                for manifest_key, report_key in _METRIC_KEYS.items()
            }
        return rows

    def captures_at(self, index: int) -> dict[str, Path]:
        """Mode -> capture file for timestamp [index]; modes that failed are skipped."""
        found: dict[str, Path] = {}
        for entry in self.manifest["modes"]:
            for rel in entry.get("captures", []):
                if Path(rel).stem == f"t{index}":
                    path = self.root / rel
                    if path.is_file():
                        found[entry["mode"]] = path
        return found

    def warnings(self) -> list[str]:
        out = []
        for entry in self.manifest["modes"]:
            out += [f"{entry['mode']}: {w}" for w in entry.get("warnings", [])]
        return out

    def describe(self) -> dict[str, str]:
        device = self.manifest.get("device", {})
        video = self.manifest.get("video", {})
        return {
            "device": f"{device.get('manufacturer', '?')} {device.get('model', '?')} "
                      f"(SDK {device.get('sdk', '?')}, {device.get('abi', '?')})",
            "app": self.manifest.get("appVersion", "?"),
            "video": f"{video.get('title', '?')} · {video.get('width', 0)}×{video.get('height', 0)} · "
                     f"{video.get('durationMs', 0) / 1000:.0f} s",
            "render size": f"{self.manifest.get('outputWidth', 0)}×{self.manifest.get('outputHeight', 0)}",
        }


def _as_float(value) -> float:
    if value is None:
        return float("nan")
    try:
        return float(value)
    except (TypeError, ValueError):
        return float("nan")


def extract(bundle_path: str | Path, dest: Path) -> Bundle:
    """Unpacks a bundle zip (or reads an already-unpacked folder) and validates it."""
    src = Path(bundle_path)
    if src.is_dir():
        root = src
    else:
        if not zipfile.is_zipfile(src):
            raise BundleError(f"{src} is neither a bundle zip nor a folder")
        with zipfile.ZipFile(src) as zf:
            _check_members(zf.namelist())
            zf.extractall(dest)
        root = dest

    manifest_path = root / "manifest.json"
    if not manifest_path.is_file():
        raise BundleError(f"no manifest.json in {src}")
    manifest = json.loads(manifest_path.read_text())

    schema = manifest.get("schema")
    if schema != SCHEMA:
        raise BundleError(f"unsupported bundle schema {schema}, this tool reads {SCHEMA}")
    if not manifest.get("modes"):
        raise BundleError("the bundle has no benchmarked modes")
    return Bundle(root, manifest)


def _check_members(names: list[str]) -> None:
    """Rejects absolute or parent-escaping paths before extracting."""
    for name in names:
        path = Path(name)
        if path.is_absolute() or ".." in path.parts:
            raise BundleError(f"unsafe path in bundle: {name}")


def perf_html(modes: list[str], rows: dict[str, dict[str, float]]) -> str:
    """The performance section; columns with no data at all are dropped."""
    keys = [k for k in PERF_KEYS if any(not _is_nan(rows.get(m, {}).get(k)) for m in modes)]
    if not keys:
        return ""
    return "<h2>Device performance</h2>" + report.perf_table(
        modes, rows, keys, PERF_LABELS, PERF_HIGHER_BETTER,
        "Measured on the device while each mode was rendering: startup and first-frame "
        "latency, sustained render FPS over the play segment, dropped frames, process CPU "
        "and RSS. Thermal status/headroom and battery temperature are context, not a score — "
        "compare modes only within one uninterrupted run.")


def _is_nan(value) -> bool:
    return value is None or value != value
