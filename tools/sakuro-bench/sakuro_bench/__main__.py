"""CLI: sakuro-bench <command>.

  device-bundle    — one zip from the app's in-app benchmark (the main scenario).
  capture-compare  — compare hand-made device screenshots of the modes.
  synth            — synthetic downscale->upscale test (self-test + baseline scalers).
"""
from __future__ import annotations

import argparse
import csv
import subprocess
import sys
import tempfile
from pathlib import Path

from . import __version__, device_bundle, fr, images, mpv_backend, nr, report

_IMG_EXT = {".png", ".jpg", ".jpeg", ".bmp", ".webp"}
_REF_NAMES = {"ref", "reference", "master", "gt", "ground_truth"}


def _parse_crop(s: str | None) -> tuple[int, int, int, int] | None:
    if not s:
        return None
    parts = [int(x) for x in s.split(",")]
    if len(parts) != 4:
        raise SystemExit("--crop expects x,y,w,h")
    return tuple(parts)  # type: ignore[return-value]


def _prep(frame: images.Frame, crop, letterbox: bool, expand: bool) -> images.Frame:
    if crop:
        frame = images.crop_rect(frame, crop)
    if letterbox:
        frame = images.autocrop_letterbox(frame)
    if expand:
        frame = images.expand_range(frame)
    return frame


def _collect(rows_fr, rows_nr, thumbs, heatmaps, originals, modes, frames, ref_frame):
    anchor = frames[modes[0]]
    ref_luma = ref_frame.luma() if ref_frame else None
    anchor_luma = anchor.luma()
    orig_b64 = report.rgb_b64(ref_frame.rgb) if ref_frame is not None else report.rgb_b64(anchor.rgb)
    for m in modes:
        fdata = frames[m]
        thumbs[m] = report.rgb_b64(fdata.rgb)
        rows_nr[m] = nr.compute(fdata.luma())
        if ref_frame is not None:
            rows_fr[m] = fr.compute(fdata.rgb, ref_frame.rgb)
            heatmaps[m] = report._b64_png(report.diff_heatmap(fdata.luma(), ref_luma))
            originals[m] = orig_b64
        else:
            heatmaps[m] = report._b64_png(report.diff_heatmap(fdata.luma(), anchor_luma))
            if m != modes[0]:  # the baseline is not its own "original"
                originals[m] = orig_b64


def cmd_capture_compare(args) -> int:
    cap_dir = Path(args.captures)
    files = sorted(p for p in cap_dir.iterdir() if p.suffix.lower() in _IMG_EXT)
    mode_files = [p for p in files if p.stem.lower() not in _REF_NAMES]
    if not mode_files:
        raise SystemExit(f"no mode screenshots in {cap_dir}")

    crop = _parse_crop(args.crop)
    frames: dict[str, images.Frame] = {}
    for p in mode_files:
        frames[p.stem] = _prep(images.load(str(p), p.stem), crop, not args.no_letterbox, args.expand_range)

    baseline = args.baseline or sorted(frames)[0]
    if baseline not in frames:
        raise SystemExit(f"--baseline '{baseline}' is not among the modes: {sorted(frames)}")
    base = frames[baseline]
    modes, aligned = _align_group(frames, baseline)

    # reference
    ref_frame = None
    ref_path = args.ref
    if not ref_path:
        for p in files:
            if p.stem.lower() in _REF_NAMES:
                ref_path = str(p)
                break
    if ref_path:
        fr.ensure_ffmpeg()
        rf = _prep(images.load(ref_path, "ref"), None, not args.no_letterbox, False)
        rf = images.resize_to(rf, base.size)
        rf, _ = images.align_translation(base, rf)
        ref_frame = rf

    rows_fr, rows_nr, thumbs, heatmaps, originals = {}, {}, {}, {}, {}
    _collect(rows_fr, rows_nr, thumbs, heatmaps, originals, modes, aligned, ref_frame)

    meta = {
        "scenario": "capture-compare" + ("  (FR+NR)" if ref_frame else "  (NR only)"),
        "generated": report.now_iso(),
        "captures": str(cap_dir),
        "reference": ref_path or "none — no-reference only",
        "baseline/grid": baseline,
        "geometry": f"{base.size[0]}×{base.size[1]}",
        "modes": len(modes),
        "sakuro-bench": __version__,
    }
    zoom_series = ([("reference", report.rgb_b64(ref_frame.rgb, 1500))] if ref_frame else []) + \
        [(m, report.rgb_b64(aligned[m].rgb, 1500)) for m in modes]
    aspect = base.size[0] / base.size[1]

    out = Path(args.out)
    index = report.write_reports(out, modes, rows_fr, rows_nr, thumbs, heatmaps, originals, meta,
                                 zoom_series=zoom_series, zoom_aspect=aspect)
    print(f"OK · report: {index}")
    print(f"     CSV:   {out / 'metrics.csv'}")
    return 0


def _align_group(frames: dict[str, images.Frame], baseline: str) -> tuple[list[str], dict[str, images.Frame]]:
    """Brings every mode onto the baseline's pixel grid; returns (ordered modes, aligned)."""
    base = frames[baseline]
    aligned = {baseline: base}
    for mode, frame in frames.items():
        if mode == baseline:
            continue
        aligned[mode], _ = images.align_translation(base, frame)
    return [baseline] + sorted(m for m in aligned if m != baseline), aligned


def cmd_device_bundle(args) -> int:
    out = Path(args.out)
    with tempfile.TemporaryDirectory() as td:
        bundle = device_bundle.extract(args.bundle, Path(td) / "bundle")
        modes_all = bundle.modes
        timestamps = bundle.timestamps or [0]
        perf_rows = bundle.perf_rows()
        print(f"bundle: {len(modes_all)} modes · {len(timestamps)} capture points")
        for warning in bundle.warnings():
            print(f"  ! {warning}")

        group_links = []
        for index, ts in enumerate(timestamps):
            files = bundle.captures_at(index)
            if not files:
                print(f"  · t{index}: no captures, skipped")
                continue
            frames = {mode: images.load(str(path), mode) for mode, path in files.items()}
            baseline = args.baseline if args.baseline in frames else _default_baseline(frames)
            modes, aligned = _align_group(frames, baseline)

            ref_frame = None
            ref_path = _ref_for(args.ref_dir, index)
            if ref_path:
                fr.ensure_ffmpeg()
                rf = images.load(str(ref_path), "ref")
                rf = images.resize_to(rf, aligned[baseline].size)
                rf, _ = images.align_translation(aligned[baseline], rf)
                ref_frame = rf

            rows_fr, rows_nr, thumbs, heatmaps, originals = {}, {}, {}, {}, {}
            _collect(rows_fr, rows_nr, thumbs, heatmaps, originals, modes, aligned, ref_frame)

            geometry = aligned[baseline].size
            meta = {
                "scenario": f"device bundle · capture t{index} @ {ts} ms"
                            + ("  (FR+NR)" if ref_frame else "  (NR only)"),
                "generated": report.now_iso(),
                **bundle.describe(),
                "reference": str(ref_path) if ref_path else "none — no-reference only",
                "baseline/grid": baseline,
                "geometry": f"{geometry[0]}×{geometry[1]}",
                "sakuro-bench": __version__,
            }
            zoom_series = ([("reference", report.rgb_b64(ref_frame.rgb, 1500))] if ref_frame else []) + \
                [(m, report.rgb_b64(aligned[m].rgb, 1500)) for m in modes]

            group_dir = out / f"t{index}"
            index_path = report.write_reports(
                group_dir, modes, rows_fr, rows_nr, thumbs, heatmaps, originals, meta,
                zoom_series=zoom_series, zoom_aspect=geometry[0] / geometry[1],
                extra_html=device_bundle.perf_html(modes, perf_rows),
                extra_json={"perf": perf_rows, "manifest": bundle.manifest},
            )
            group_links.append((f"t{index} @ {ts} ms", f"t{index}/index.html", index_path))
            print(f"  · t{index}: {len(modes)} modes → {index_path}")

        if not group_links:
            raise SystemExit("the bundle contains no usable captures")

        summary = _write_bundle_summary(out, bundle, perf_rows, group_links)

    print(f"OK · summary: {summary}")
    for label, _, path in group_links:
        print(f"     {label}: {path}")
    return 0


def _default_baseline(frames: dict[str, images.Frame]) -> str:
    """Prefer an Off mode — that is the bundle's intended baseline."""
    for mode in sorted(frames):
        if mode.endswith("_off"):
            return mode
    return sorted(frames)[0]


def _ref_for(ref_dir: str | None, index: int) -> Path | None:
    """A ground-truth frame for capture t<index>, if the user supplied a folder."""
    if not ref_dir:
        return None
    for ext in _IMG_EXT:
        candidate = Path(ref_dir) / f"t{index}{ext}"
        if candidate.is_file():
            return candidate
    return None


def _write_bundle_summary(out: Path, bundle, perf_rows, group_links) -> Path:
    """A landing page: device/video info, the perf table, links to each capture group."""
    out.mkdir(parents=True, exist_ok=True)
    modes = bundle.modes
    meta_html = "".join(f"<li><b>{k}:</b> {v}</li>" for k, v in bundle.describe().items())
    links_html = "".join(f"<li><a href='{href}'>{label}</a></li>" for label, href, _ in group_links)
    warnings = bundle.warnings()
    warn_html = ("<h2>Warnings</h2><ul class='meta'>"
                 + "".join(f"<li>{w}</li>" for w in warnings) + "</ul>") if warnings else ""
    html = f"""<!doctype html><html lang=en><head><meta charset=utf-8>
<title>sakuro-bench · device bundle</title>
<style>
body{{background:#12101a;color:#efe9f5;font:14px/1.55 system-ui,sans-serif;margin:0;padding:28px 22px;
max-width:1100px;margin-inline:auto}}
h1{{font-size:22px;margin:0 0 4px}} h2{{font-size:16px;margin:26px 0 10px;color:#e0559b}}
.sub{{color:#9a90ad;margin:0 0 18px}}
ul.meta{{list-style:none;padding:0;display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:6px}}
ul.meta li{{background:#1b1826;border:1px solid #2b2738;border-radius:9px;padding:7px 11px}}
b{{color:#9a90ad;font-weight:600}}
table{{border-collapse:collapse;width:100%;margin-top:6px}}
th,td{{border:1px solid #2b2738;padding:6px 9px;text-align:right;font-variant-numeric:tabular-nums}}
th{{color:#9a90ad;font-weight:600}} th.mode{{text-align:left;color:#efe9f5}}
thead th{{background:#1b1826}} td.best{{background:rgba(224,85,155,.22);color:#fff;font-weight:600}}
.note{{color:#9a90ad;font-size:12px;margin-top:6px}}
.dot{{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:7px}}
.legend{{display:flex;flex-wrap:wrap;gap:10px;margin:10px 0}}
.legend .li{{display:flex;align-items:center;gap:6px;color:#9a90ad;font-size:12px}}
.legend i{{width:9px;height:9px;border-radius:50%;display:inline-block}}
a{{color:#5bc8f5}}
</style></head><body>
<h1>sakuro-bench · device bundle</h1>
<p class=sub>{report.now_iso()} · sakuro-bench {__version__}</p>
<ul class=meta>{meta_html}</ul>
{device_bundle.perf_html(modes, perf_rows)}
<h2>Capture points</h2>
<ul class='meta'>{links_html}</ul>
{warn_html}
</body></html>"""
    path = out / "index.html"
    path.write_text(html, encoding="utf-8")
    _write_perf_csv(out / "performance.csv", modes, perf_rows)
    return path


def _write_perf_csv(path: Path, modes, perf_rows) -> None:
    with path.open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["mode", *device_bundle.PERF_KEYS])
        for mode in modes:
            row = perf_rows.get(mode, {})
            writer.writerow([mode, *[row.get(k, float("nan")) for k in device_bundle.PERF_KEYS]])


_SWS = {
    "neighbor": "neighbor", "bilinear": "bilinear", "bicubic": "bicubic",
    "lanczos": "lanczos", "spline": "spline",
}


def _ff_scale(src: str, dst: str, w: int, h: int, flags: str, pre: str = "") -> None:
    vf = f"{pre}scale={w}:{h}:flags={flags}"
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-i", src, "-vf", vf, "-frames:v", "1", dst], check=True)


def cmd_synth(args) -> int:
    fr.ensure_ffmpeg()
    master = images.load(args.master, "master")
    W, H = master.size
    dw, dh = W // args.scale, H // args.scale
    modes = [m.strip() for m in args.modes.split(",") if m.strip()]
    for m in modes:
        if m not in _SWS:
            raise SystemExit(f"unknown mode '{m}'. Available: {', '.join(_SWS)}")

    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        master_png = str(tdp / "master.png")
        images.save(master, master_png)
        # degradation -> input
        low = str(tdp / "low.png")
        pre = "gblur=sigma=0.7,noise=alls=8:allf=t," if args.degrade == "realistic" else ""
        _ff_scale(master_png, low, dw, dh, "lanczos", pre)

        frames: dict[str, images.Frame] = {}
        for m in modes:
            up = str(tdp / f"up_{m}.png")
            _ff_scale(low, up, W, H, _SWS[m])
            frames[m] = images.load(up, m)

        ref_luma = master.luma()
        master_b64 = report.rgb_b64(master.rgb)
        rows_fr, rows_nr, thumbs, heatmaps, originals = {}, {}, {}, {}, {}
        for m in modes:
            f = frames[m]
            rows_fr[m] = fr.compute(f.rgb, master.rgb)
            rows_nr[m] = nr.compute(f.luma())
            thumbs[m] = report.rgb_b64(f.rgb)
            heatmaps[m] = report._b64_png(report.diff_heatmap(f.luma(), ref_luma))
            originals[m] = master_b64

    meta = {
        "scenario": f"synth downscale→upscale ×{args.scale} ({args.degrade})",
        "generated": report.now_iso(),
        "master": f"{args.master} ({W}×{H})",
        "input": f"{dw}×{dh}",
        "degradation": args.degrade,
        "reference": "master (ground-truth)",
        "sakuro-bench": __version__,
    }
    zoom_series = [("reference", report.rgb_b64(master.rgb, 1500))] + \
        [(m, report.rgb_b64(frames[m].rgb, 1500)) for m in modes]

    out = Path(args.out)
    index = report.write_reports(out, modes, rows_fr, rows_nr, thumbs, heatmaps, originals, meta,
                                 zoom_series=zoom_series, zoom_aspect=W / H)
    print(f"OK · report: {index}")
    print("     Note: synth runs only ffmpeg scalers (baseline). The real")
    print("     engine-mpv modes are the `synth-mpv` command (offline mpv + Anime4K).")
    return 0


def cmd_synth_mpv(args) -> int:
    fr.ensure_ffmpeg()
    mpv_backend.ensure_mpv()
    master = images.load(args.master, "master")
    W, H = master.size
    dw, dh = W // args.scale, H // args.scale
    modes = [m.strip() for m in args.modes.split(",") if m.strip()]
    for m in modes:
        if m not in mpv_backend.PRESETS:
            raise SystemExit(f"unknown preset '{m}'. Available: {', '.join(mpv_backend.PRESETS)}")

    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        master_png = str(tdp / "master.png")
        images.save(master, master_png)
        low = str(tdp / "low.png")
        pre = "gblur=sigma=0.7,noise=alls=8:allf=t," if args.degrade == "realistic" else ""
        _ff_scale(master_png, low, dw, dh, "lanczos", pre)

        ref_luma = master.luma()
        master_b64 = report.rgb_b64(master.rgb)
        rows_fr, rows_nr, thumbs, heatmaps, originals, zoom = {}, {}, {}, {}, {}, {}
        for m in modes:
            out_png = str(tdp / f"mpv_{m}.png")
            mpv_backend.render(low, out_png, (W, H), m)
            f = images.resize_to(images.load(out_png, m), (W, H))
            rows_fr[m] = fr.compute(f.rgb, master.rgb)
            rows_nr[m] = nr.compute(f.luma())
            thumbs[m] = report.rgb_b64(f.rgb)
            heatmaps[m] = report._b64_png(report.diff_heatmap(f.luma(), ref_luma))
            originals[m] = master_b64
            zoom[m] = report.rgb_b64(f.rgb, 1500)
            print(f"  · {m}: rendered")
        zoom_series = [("reference", report.rgb_b64(master.rgb, 1500))] + [(m, zoom[m]) for m in modes]

    meta = {
        "scenario": f"synth-mpv (offline headless mpv + Anime4K) ×{args.scale} ({args.degrade})",
        "generated": report.now_iso(),
        "master": f"{args.master} ({W}×{H})",
        "input": f"{dw}×{dh}",
        "engine": "mpv " + _mpv_ver(),
        "reference": "master (ground-truth)",
        "sakuro-bench": __version__,
    }
    out = Path(args.out)
    index = report.write_reports(out, modes, rows_fr, rows_nr, thumbs, heatmaps, originals, meta,
                                 zoom_series=zoom_series, zoom_aspect=W / H)
    print(f"OK · report: {index}")
    print("     Note: offline-mpv ≈ engine-mpv (FP32 vs a possible FP16 CNN on mobile).")
    print("     Cross-check absolute numbers with a device capture; the ranking is reliable.")
    return 0


def _mpv_ver() -> str:
    try:
        out = subprocess.run(["mpv", "--version"], capture_output=True, text=True).stdout
        return out.splitlines()[0].split()[1] if out else "?"
    except (OSError, IndexError):
        return "?"


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(prog="sakuro-bench", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--version", action="version", version=f"sakuro-bench {__version__}")
    sub = ap.add_subparsers(dest="cmd", required=True)

    db = sub.add_parser("device-bundle", help="report from the app's in-app benchmark bundle (zip)")
    db.add_argument("--bundle", required=True, help="bundle zip exported by the app (or an unpacked folder)")
    db.add_argument("--baseline", help="anchor mode for alignment (default: the *_off mode)")
    db.add_argument("--ref-dir", help="folder with ground-truth frames t0.png, t1.png… for FR metrics")
    db.add_argument("--out", default="report", help="report folder (default ./report)")
    db.set_defaults(func=cmd_device_bundle)

    cc = sub.add_parser("capture-compare", help="compare device screenshots of the modes")
    cc.add_argument("--captures", required=True, help="folder with screenshots (file name = mode)")
    cc.add_argument("--ref", help="reference master for FR (otherwise NR only; a ref.* file is also searched)")
    cc.add_argument("--baseline", help="anchor mode for alignment (default: first)")
    cc.add_argument("--crop", help="manual UI crop: x,y,w,h")
    cc.add_argument("--no-letterbox", action="store_true", help="do not trim black bars")
    cc.add_argument("--expand-range", action="store_true", help="expand limited(16..235)→full")
    cc.add_argument("--out", default="report", help="report folder (default ./report)")
    cc.set_defaults(func=cmd_capture_compare)

    sy = sub.add_parser("synth", help="synthetic downscale→upscale test")
    sy.add_argument("--master", required=True, help="high-resolution reference frame")
    sy.add_argument("--scale", type=int, default=2, help="factor (default 2)")
    sy.add_argument("--degrade", choices=["clean", "realistic"], default="clean")
    sy.add_argument("--modes", default="bilinear,bicubic,lanczos", help="ffmpeg scalers, comma-separated")
    sy.add_argument("--out", default="report", help="report folder")
    sy.set_defaults(func=cmd_synth)

    sm = sub.add_parser("synth-mpv", help="offline mpv + Anime4K downscale→upscale test")
    sm.add_argument("--master", required=True, help="high-resolution reference frame")
    sm.add_argument("--scale", type=int, default=2)
    sm.add_argument("--degrade", choices=["clean", "realistic"], default="clean")
    sm.add_argument("--modes", default="off,mpv_ewa,anime4k_s,anime4k_m",
                    help="mpv presets: " + ",".join(mpv_backend.PRESETS))
    sm.add_argument("--out", default="report")
    sm.set_defaults(func=cmd_synth_mpv)

    args = ap.parse_args(argv)
    try:
        return args.func(args)
    except (fr.FfmpegError, mpv_backend.MpvError, device_bundle.BundleError,
            subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
