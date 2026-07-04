"""CLI: sakuro-bench <команда>.

  capture-compare  — сравнить скриншоты режимов с устройства (главный сценарий).
  synth            — синтетический downscale->upscale тест (self-test + baseline-скейлеры).
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

from . import __version__, fr, images, mpv_backend, nr, report

_IMG_EXT = {".png", ".jpg", ".jpeg", ".bmp", ".webp"}
_REF_NAMES = {"ref", "reference", "master", "gt", "ground_truth"}


def _parse_crop(s: str | None) -> tuple[int, int, int, int] | None:
    if not s:
        return None
    parts = [int(x) for x in s.split(",")]
    if len(parts) != 4:
        raise SystemExit("--crop ожидает x,y,w,h")
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
            if m != modes[0]:  # baseline сам себе не «оригинал»
                originals[m] = orig_b64


def cmd_capture_compare(args) -> int:
    cap_dir = Path(args.captures)
    files = sorted(p for p in cap_dir.iterdir() if p.suffix.lower() in _IMG_EXT)
    mode_files = [p for p in files if p.stem.lower() not in _REF_NAMES]
    if not mode_files:
        raise SystemExit(f"в {cap_dir} нет скриншотов режимов")

    crop = _parse_crop(args.crop)
    frames: dict[str, images.Frame] = {}
    for p in mode_files:
        frames[p.stem] = _prep(images.load(str(p), p.stem), crop, not args.no_letterbox, args.expand_range)

    baseline = args.baseline or sorted(frames)[0]
    if baseline not in frames:
        raise SystemExit(f"--baseline '{baseline}' нет среди режимов: {sorted(frames)}")
    base = frames[baseline]
    # все режимы на общую сетку baseline
    aligned = {baseline: base}
    for m, f in frames.items():
        if m == baseline:
            continue
        af, shift = images.align_translation(base, f)
        aligned[m] = af
    modes = [baseline] + sorted(m for m in aligned if m != baseline)

    # эталон
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
        "scenario": "capture-compare" + ("  (FR+NR)" if ref_frame else "  (только NR)"),
        "generated": report.now_iso(),
        "captures": str(cap_dir),
        "эталон": ref_path or "нет — только no-reference",
        "baseline/сетка": baseline,
        "геометрия": f"{base.size[0]}×{base.size[1]}",
        "режимов": len(modes),
        "sakuro-bench": __version__,
    }
    zoom_series = ([("эталон", report.rgb_b64(ref_frame.rgb, 1500))] if ref_frame else []) + \
        [(m, report.rgb_b64(aligned[m].rgb, 1500)) for m in modes]
    aspect = base.size[0] / base.size[1]

    out = Path(args.out)
    index = report.write_reports(out, modes, rows_fr, rows_nr, thumbs, heatmaps, originals, meta,
                                 zoom_series=zoom_series, zoom_aspect=aspect)
    print(f"OK · отчёт: {index}")
    print(f"     CSV:   {out / 'metrics.csv'}")
    return 0


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
            raise SystemExit(f"неизвестный режим '{m}'. Доступно: {', '.join(_SWS)}")

    with tempfile.TemporaryDirectory() as td:
        tdp = Path(td)
        master_png = str(tdp / "master.png")
        images.save(master, master_png)
        # деградация -> вход
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
        "мастер": f"{args.master} ({W}×{H})",
        "вход": f"{dw}×{dh}",
        "деградация": args.degrade,
        "эталон": "мастер (ground-truth)",
        "sakuro-bench": __version__,
    }
    zoom_series = [("эталон", report.rgb_b64(master.rgb, 1500))] + \
        [(m, report.rgb_b64(frames[m].rgb, 1500)) for m in modes]

    out = Path(args.out)
    index = report.write_reports(out, modes, rows_fr, rows_nr, thumbs, heatmaps, originals, meta,
                                 zoom_series=zoom_series, zoom_aspect=W / H)
    print(f"OK · отчёт: {index}")
    print("     Прим.: synth гоняет только ffmpeg-скейлеры (baseline). Реальные")
    print("     engine-mpv-режимы — команда `synth-mpv` (offline mpv + Anime4K).")
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
            raise SystemExit(f"неизвестный пресет '{m}'. Доступно: {', '.join(mpv_backend.PRESETS)}")

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
            print(f"  · {m}: отрисован")
        zoom_series = [("эталон", report.rgb_b64(master.rgb, 1500))] + [(m, zoom[m]) for m in modes]

    meta = {
        "scenario": f"synth-mpv (offline headless mpv + Anime4K) ×{args.scale} ({args.degrade})",
        "generated": report.now_iso(),
        "мастер": f"{args.master} ({W}×{H})",
        "вход": f"{dw}×{dh}",
        "движок": "mpv " + _mpv_ver(),
        "эталон": "мастер (ground-truth)",
        "sakuro-bench": __version__,
    }
    out = Path(args.out)
    index = report.write_reports(out, modes, rows_fr, rows_nr, thumbs, heatmaps, originals, meta,
                                 zoom_series=zoom_series, zoom_aspect=W / H)
    print(f"OK · отчёт: {index}")
    print("     Прим.: offline-mpv ≈ engine-mpv (FP32 vs возможный FP16-CNN на мобилке).")
    print("     Абсолютные числа сверять с device-capture; ранжир достоверен.")
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

    cc = sub.add_parser("capture-compare", help="сравнить скриншоты режимов с устройства")
    cc.add_argument("--captures", required=True, help="папка со скриншотами (имя файла = режим)")
    cc.add_argument("--ref", help="эталон-мастер для FR (иначе только NR; ищется и файл ref.*)")
    cc.add_argument("--baseline", help="режим-якорь для выравнивания (по умолч. первый)")
    cc.add_argument("--crop", help="ручной кроп UI: x,y,w,h")
    cc.add_argument("--no-letterbox", action="store_true", help="не резать чёрные поля")
    cc.add_argument("--expand-range", action="store_true", help="раскрыть limited(16..235)→full")
    cc.add_argument("--out", default="report", help="папка отчёта (по умолч. ./report)")
    cc.set_defaults(func=cmd_capture_compare)

    sy = sub.add_parser("synth", help="синтетический downscale→upscale тест")
    sy.add_argument("--master", required=True, help="эталонный кадр высокого разрешения")
    sy.add_argument("--scale", type=int, default=2, help="коэффициент (по умолч. 2)")
    sy.add_argument("--degrade", choices=["clean", "realistic"], default="clean")
    sy.add_argument("--modes", default="bilinear,bicubic,lanczos", help="ffmpeg-скейлеры через запятую")
    sy.add_argument("--out", default="report", help="папка отчёта")
    sy.set_defaults(func=cmd_synth)

    sm = sub.add_parser("synth-mpv", help="offline mpv + Anime4K downscale→upscale тест")
    sm.add_argument("--master", required=True, help="эталонный кадр высокого разрешения")
    sm.add_argument("--scale", type=int, default=2)
    sm.add_argument("--degrade", choices=["clean", "realistic"], default="clean")
    sm.add_argument("--modes", default="off,mpv_ewa,anime4k_s,anime4k_m",
                    help="пресеты mpv: " + ",".join(mpv_backend.PRESETS))
    sm.add_argument("--out", default="report")
    sm.set_defaults(func=cmd_synth_mpv)

    args = ap.parse_args(argv)
    try:
        return args.func(args)
    except (fr.FfmpegError, mpv_backend.MpvError, subprocess.CalledProcessError) as exc:
        print(f"ОШИБКА: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
