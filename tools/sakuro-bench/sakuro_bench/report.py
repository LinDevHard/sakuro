"""Report generation: diff heatmaps, before/after sliders, SVG infographics, HTML+CSV+JSON."""
from __future__ import annotations

import base64
import csv
import io
import json
import math
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from PIL import Image

from . import fr, nr

# mode palette (shared by charts and legend)
PALETTE = ["#e0559b", "#5bc8f5", "#8bd450", "#f5a623", "#b06be0", "#f2545b", "#38d9a9", "#ffd93d"]

# --- colormap for heatmaps (turbo-like, no matplotlib) ---
_CMAP = np.array(
    [(48, 18, 59), (50, 100, 200), (30, 180, 160),
     (150, 200, 40), (240, 180, 30), (220, 60, 20), (140, 10, 10)],
    dtype=np.float64,
)


def _colorize(norm: np.ndarray) -> np.ndarray:
    n = _CMAP.shape[0] - 1
    pos = np.clip(norm, 0, 1) * n
    lo = np.clip(np.floor(pos).astype(int), 0, n - 1)
    frac = (pos - lo)[..., None]
    rgb = _CMAP[lo] * (1 - frac) + _CMAP[lo + 1] * frac
    return rgb.astype(np.uint8)


def diff_heatmap(dist_luma: np.ndarray, ref_luma: np.ndarray) -> Image.Image:
    d = np.abs(dist_luma - ref_luma)
    p99 = np.percentile(d, 99) + 1e-6
    return Image.fromarray(_colorize(d / p99), "RGB")


def rgb_b64(rgb: np.ndarray, max_w: int = 560) -> str:
    return _b64_png(Image.fromarray(rgb, "RGB"), max_w)


def _b64_png(img: Image.Image, max_w: int = 560) -> str:
    if img.width > max_w:
        h = round(img.height * max_w / img.width)
        img = img.resize((max_w, h), Image.Resampling.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def _best_index(values: list[float], higher_better: bool) -> int | None:
    valid = [(i, v) for i, v in enumerate(values) if not math.isnan(v)]
    if not valid:
        return None
    return (max if higher_better else min)(valid, key=lambda t: t[1])[0]


def _fmt(v: float) -> str:
    return "—" if math.isnan(v) else (f"{v:.4f}" if abs(v) < 100 else f"{v:.2f}")


def _color(i: int) -> str:
    return PALETTE[i % len(PALETTE)]


def now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M %Z")


# ============================ SVG infographics ============================
def _norm(vals: list[float], invert: bool = False, lo: float = 0.12) -> list[float]:
    """Min-max normalization into [lo,1] over the valid values; nan→0."""
    v = [x for x in vals if not math.isnan(x)]
    if not v:
        return [0.0] * len(vals)
    a, b = min(v), max(v)
    rng = (b - a) or 1.0
    out = []
    for x in vals:
        if math.isnan(x):
            out.append(0.0)
            continue
        t = (x - a) / rng
        if invert:
            t = 1 - t
        out.append(lo + (1 - lo) * t)
    return out


def svg_vmaf_gap(modes, fr_rows) -> str:
    """Grouped VMAF vs VMAF-NEG bars; the gap = "gain from enhancement" (sharpening cheat)."""
    order = sorted(modes, key=lambda m: fr_rows.get(m, {}).get("vmaf", float("nan")), reverse=True)
    order = [m for m in order if not math.isnan(fr_rows.get(m, {}).get("vmaf", float("nan")))]
    if not order:
        return ""
    W, H, pad, top = 660, 340, 46, 28
    bw = (W - 2 * pad) / max(len(order), 1)
    ymax, y0 = 100.0, H - 40
    bars = ""
    for i, m in enumerate(order):
        vmaf = fr_rows[m]["vmaf"]
        neg = fr_rows[m].get("vmaf_neg", float("nan"))
        cx = pad + i * bw + bw / 2
        c = _color(modes.index(m))
        hv = (vmaf / ymax) * (y0 - top)
        bars += f"<rect x='{cx-26:.1f}' y='{y0-hv:.1f}' width='24' height='{hv:.1f}' fill='{c}' rx='3'/>"
        bars += f"<text x='{cx-14:.1f}' y='{y0-hv-6:.1f}' class='v'>{vmaf:.1f}</text>"
        if not math.isnan(neg):
            hn = (neg / ymax) * (y0 - top)
            bars += (f"<rect x='{cx+2:.1f}' y='{y0-hn:.1f}' width='24' height='{hn:.1f}' "
                     f"fill='{c}' opacity='0.35' stroke='{c}' rx='3'/>")
            bars += f"<text x='{cx+14:.1f}' y='{y0-hn-6:.1f}' class='v'>{neg:.1f}</text>"
            gap = vmaf - neg
            bars += f"<text x='{cx:.1f}' y='{y0+30:.1f}' class='gap'>Δ{gap:.1f}</text>"
        bars += f"<text x='{cx:.1f}' y='{y0+16:.1f}' class='lbl'>{m}</text>"
    grid = "".join(
        f"<line x1='{pad}' y1='{y0-(g/ymax)*(y0-top):.1f}' x2='{W-pad}' y2='{y0-(g/ymax)*(y0-top):.1f}' class='grid'/>"
        f"<text x='{pad-8}' y='{y0-(g/ymax)*(y0-top)+4:.1f}' class='ax'>{g}</text>"
        for g in (0, 25, 50, 75, 100))
    return (f"<svg viewBox='0 0 {W} {H}' class='chart'>{grid}{bars}"
            f"<text x='{W-pad}' y='16' class='cap'>█ VMAF   ▢ VMAF-NEG   Δ=gain from enhancement</text></svg>")


def svg_scatter(modes, nr_rows) -> str:
    """Sharpness × Ringing: top-right = oversharpened (bad), bottom-left = soft/clean."""
    sh = [nr_rows.get(m, {}).get("sharpness", float("nan")) for m in modes]
    ri = [nr_rows.get(m, {}).get("ringing", float("nan")) for m in modes]
    if all(math.isnan(x) for x in sh):
        return ""
    W, H, pad = 520, 340, 52
    nsh, nri = _norm(sh, lo=0.0), _norm(ri, lo=0.0)
    x0, x1, y0, y1 = pad, W - 20, H - 44, 24
    pts = ""
    for i, m in enumerate(modes):
        px = x0 + nsh[i] * (x1 - x0)
        py = y0 - nri[i] * (y0 - y1)
        c = _color(i)
        pts += f"<circle cx='{px:.1f}' cy='{py:.1f}' r='7' fill='{c}'/>"
        pts += f"<text x='{px+10:.1f}' y='{py+4:.1f}' class='lbl' style='text-anchor:start'>{m}</text>"
    return (f"<svg viewBox='0 0 {W} {H}' class='chart'>"
            f"<line x1='{x0}' y1='{y0}' x2='{x1}' y2='{y0}' class='axl'/>"
            f"<line x1='{x0}' y1='{y0}' x2='{x0}' y2='{y1}' class='axl'/>"
            f"<text x='{(x0+x1)/2:.0f}' y='{H-12}' class='ax'>sharpness →</text>"
            f"<text x='16' y='{(y0+y1)/2:.0f}' class='ax' transform='rotate(-90 16 {(y0+y1)/2:.0f})'>ringing →</text>"
            f"<text x='{x1}' y='{y1+2}' class='cap' style='text-anchor:end'>oversharp</text>{pts}</svg>")


def svg_radar(modes, fr_rows, nr_rows, has_fr) -> str:
    """Quality profile: axes normalized across modes; larger area = better."""
    axes: list[tuple[str, list[float], bool]] = []
    if has_fr:
        for k, lab in (("vmaf", "VMAF"), ("float_ssim", "SSIM"),
                       ("float_ms_ssim", "MS-SSIM"), ("psnr_y", "PSNR")):
            axes.append((lab, [fr_rows.get(m, {}).get(k, float("nan")) for m in modes], False))
    axes.append(("cleanliness", [nr_rows.get(m, {}).get("noise", float("nan")) for m in modes], True))
    axes.append(("low ringing", [nr_rows.get(m, {}).get("ringing", float("nan")) for m in modes], True))
    if len(axes) < 3:
        return ""
    normed = [_norm(vals, invert=inv) for _, vals, inv in axes]
    W = H = 360
    cx, cy, R = W / 2, H / 2 + 6, 118
    n = len(axes)
    def pt(ai, r):
        ang = -math.pi / 2 + 2 * math.pi * ai / n
        return cx + R * r * math.cos(ang), cy + R * r * math.sin(ang)
    rings = "".join(
        "<polygon points='" + " ".join(f"{pt(a, g)[0]:.1f},{pt(a, g)[1]:.1f}" for a in range(n))
        + "' class='rgrid'/>" for g in (0.25, 0.5, 0.75, 1.0))
    spokes, labels = "", ""
    for ai, (lab, _, _) in enumerate(axes):
        ex, ey = pt(ai, 1.0)
        spokes += f"<line x1='{cx}' y1='{cy}' x2='{ex:.1f}' y2='{ey:.1f}' class='rgrid'/>"
        lx, ly = pt(ai, 1.16)
        labels += f"<text x='{lx:.1f}' y='{ly:.1f}' class='rax'>{lab}</text>"
    polys = ""
    for i, m in enumerate(modes):
        pts = " ".join(f"{pt(a, normed[a][i])[0]:.1f},{pt(a, normed[a][i])[1]:.1f}" for a in range(n))
        c = _color(i)
        polys += f"<polygon points='{pts}' fill='{c}' fill-opacity='0.14' stroke='{c}' stroke-width='2'/>"
    return f"<svg viewBox='0 0 {W} {H}' class='chart radar'>{rings}{spokes}{polys}{labels}</svg>"


def _legend(modes) -> str:
    items = "".join(
        f"<span class='li'><i style='background:{_color(i)}'></i>{m}</span>" for i, m in enumerate(modes))
    return f"<div class='legend'>{items}</div>"


# ============================ tables ============================
def _table(modes, rows, keys, labels, higher_better, note) -> str:
    best = {}
    for k in keys:
        hb = higher_better.get(k)
        best[k] = None if hb is None else _best_index(
            [rows.get(m, {}).get(k, float("nan")) for m in modes], hb)
    head = "".join(f"<th>{labels[k]}</th>" for k in keys)
    body = ""
    for i, m in enumerate(modes):
        cells = "".join(
            f"<td{' class=best' if best.get(k) == i else ''}>{_fmt(rows.get(m, {}).get(k, float('nan')))}</td>"
            for k in keys)
        body += (f"<tr><th class='mode'><i class='dot' style='background:{_color(i)}'></i>{m}</th>{cells}</tr>")
    return (f"<table><thead><tr><th>Mode</th>{head}</tr></thead><tbody>{body}</tbody></table>"
            f"<p class='note'>{note}</p>")


# ============================ synchronized 1:1 zoom ============================
def zoom_panel(series: list[tuple[str, str]], aspect: float) -> str:
    """series = [(label, b64-hires), …]; hovering the navigator moves the loupe in all of them."""
    if not series:
        return ""
    nav_b64 = series[0][1]
    cells = "".join(
        f"<div class='zcell'><span>{lab}</span>"
        f"<div class='zview' style=\"background-image:url('{b64}')\"></div></div>"
        for lab, b64 in series)
    return (f"<div class='zoom' style='--asp:{aspect:.4f}'>"
            f"<div class='znav'><img src='{nav_b64}'><div class='zbox'></div>"
            f"<span class='zhint'>navigator ({series[0][0]}) — move the cursor</span></div>"
            f"<div class='zrow'>{cells}</div></div>")


# ============================ before/after slider ============================
def _slider(mode, original_b64, enhanced_b64) -> str:
    return (f"<div class='ba'>"
            f"<img class='ba-base' src='{enhanced_b64}'>"
            f"<div class='ba-over'><img src='{original_b64}'></div>"
            f"<div class='ba-handle'></div>"
            f"<input type='range' min='0' max='100' value='50' class='ba-range' aria-label='{mode}'>"
            f"<span class='ba-tag l'>Original</span><span class='ba-tag r'>Enhanced</span></div>")


# ============================ output ============================
def write_reports(out_dir, modes, fr_rows, nr_rows, thumbs, heatmaps, originals, meta,
                  zoom_series=None, zoom_aspect=1.777) -> Path:
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    has_fr = any(fr_rows.values())

    fr_keys = fr.FR_KEYS if has_fr else []
    with (out_dir / "metrics.csv").open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["mode", *fr_keys, *nr.NR_KEYS])
        for m in modes:
            w.writerow([m,
                        *[_fmt(fr_rows.get(m, {}).get(k, float("nan"))) for k in fr_keys],
                        *[_fmt(nr_rows.get(m, {}).get(k, float("nan"))) for k in nr.NR_KEYS]])

    (out_dir / "metrics.json").write_text(
        json.dumps({"meta": meta, "fr": fr_rows, "nr": nr_rows}, indent=2, ensure_ascii=False))

    index = out_dir / "index.html"
    index.write_text(_render_html(modes, fr_rows, nr_rows, thumbs, heatmaps, originals, meta,
                                  has_fr, zoom_series or [], zoom_aspect), encoding="utf-8")
    return index


def _render_html(modes, fr_rows, nr_rows, thumbs, heatmaps, originals, meta, has_fr,
                 zoom_series, zoom_aspect) -> str:
    fr_html = ""
    if has_fr:
        fr_html = "<h2>Full-reference (reference known)</h2>" + _table(
            modes, fr_rows, fr.FR_KEYS, fr.FR_LABELS, fr.FR_HIGHER_BETTER,
            "Higher = better. VMAF-NEG penalizes \"cheating\" sharpening: if a mode is high on VMAF "
            "but drops on NEG, the gain comes mostly from contrast/sharpness, not detail.")
    nr_html = "<h2>No-reference (no reference needed)</h2>" + _table(
        modes, nr_rows, nr.NR_KEYS, nr.NR_LABELS, nr.NR_HIGHER_BETTER,
        "Relative descriptors, NOT a MOS. Sharpness/HF are not highlighted — they cannot be "
        "maximized in isolation. Noise and ringing: lower is better.")

    charts = "<div class='charts'>"
    gap = svg_vmaf_gap(modes, fr_rows) if has_fr else ""
    if gap:
        charts += f"<div class='cbox'><h3>VMAF vs NEG — gain from enhancement</h3>{gap}</div>"
    charts += f"<div class='cbox'><h3>Quality profile</h3>{svg_radar(modes, fr_rows, nr_rows, has_fr)}</div>"
    charts += f"<div class='cbox'><h3>Sharpness × Ringing (oversharp)</h3>{svg_scatter(modes, nr_rows)}</div>"
    charts += "</div>"

    cards = ""
    for i, m in enumerate(modes):
        slider = _slider(m, originals[m], thumbs[m]) if m in originals else \
            f"<img src='{thumbs[m]}' class='single'>"
        hm = (f"<figure><img src='{heatmaps[m]}'><figcaption>diff vs reference</figcaption></figure>"
              if m in heatmaps else "")
        cards += (f"<div class='card'><h3><i class='dot' style='background:{_color(i)}'></i>{m}</h3>"
                  f"{slider}{hm}</div>")

    zoom_html = ""
    if zoom_series:
        zoom_html = ("<h2>1:1 zoom — move the cursor over the navigator (synced across all)</h2>"
                     + zoom_panel(zoom_series, zoom_aspect))

    meta_html = "".join(f"<li><b>{k}:</b> {v}</li>" for k, v in meta.items())
    return f"""<!doctype html><html lang=en><head><meta charset=utf-8>
<title>sakuro-bench — {meta.get('scenario','report')}</title>
<style>
:root{{--bg:#0e0b14;--fg:#ece7f2;--mut:#9a8fb0;--acc:#e0559b;--card:#181125;--line:#2a2038}}
*{{box-sizing:border-box}}body{{margin:0;padding:32px;background:var(--bg);color:var(--fg);
font:15px/1.5 -apple-system,Segoe UI,Inter,sans-serif}}
h1{{margin:0 0 4px;font-size:26px}}h2{{margin:36px 0 12px;font-size:18px;color:var(--acc)}}
h3{{margin:0 0 10px;font-size:14px;display:flex;align-items:center;gap:7px}}.sub{{color:var(--mut);margin:0 0 16px}}
i.dot{{width:11px;height:11px;border-radius:3px;display:inline-block}}
table{{border-collapse:collapse;width:100%;background:var(--card);border-radius:10px;overflow:hidden}}
th,td{{padding:9px 13px;text-align:right;border-bottom:1px solid var(--line)}}
thead th{{background:#20182f;color:var(--mut);font-weight:600}}
th.mode,tbody th{{text-align:left;font-weight:600}}th.mode i{{margin-right:7px}}
td.best{{background:rgba(224,85,155,.22);color:#fff;font-weight:700}}
.note{{color:var(--mut);font-size:13px;margin:8px 2px 0;max-width:82ch}}
ul.meta{{list-style:none;padding:0;color:var(--mut);font-size:13px;columns:2}}
.legend{{display:flex;flex-wrap:wrap;gap:14px;margin:6px 0 4px;font-size:13px;color:var(--mut)}}
.legend .li{{display:flex;align-items:center;gap:6px}}.legend i{{width:11px;height:11px;border-radius:3px}}
.charts{{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:16px;margin-top:12px}}
.cbox{{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px}}
svg.chart{{width:100%;height:auto}}
.grid{{stroke:#2a2038;stroke-width:1}}.axl{{stroke:#4a3d5e;stroke-width:1.5}}
.rgrid{{stroke:#2f2542;fill:none;stroke-width:1}}
text{{fill:var(--mut);font:11px sans-serif}}.v{{fill:var(--fg);font-size:10px;text-anchor:middle}}
.lbl{{fill:var(--fg);font-size:11px;text-anchor:middle}}.gap{{fill:var(--acc);font-size:11px;text-anchor:middle;font-weight:700}}
.ax{{font-size:10px;text-anchor:middle}}.cap{{fill:var(--mut);font-size:11px;text-anchor:end}}.rax{{font-size:11px;text-anchor:middle}}
.gallery{{display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:16px;margin-top:16px}}
.card{{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px}}
.card .single{{width:100%;border-radius:8px;display:block}}
figure{{margin:10px 0 0}}figcaption{{color:var(--mut);font-size:12px;margin-top:4px;text-align:center}}
figure img{{width:100%;border-radius:8px;display:block}}
.ba{{position:relative;overflow:hidden;border-radius:8px;user-select:none;line-height:0}}
.ba-base{{width:100%;display:block}}
.ba-over{{position:absolute;top:0;left:0;height:100%;width:50%;overflow:hidden}}
.ba-over img{{position:absolute;top:0;left:0;height:100%;max-width:none}}
.ba-handle{{position:absolute;top:0;left:50%;width:2px;height:100%;background:#fff;transform:translateX(-1px);
box-shadow:0 0 0 1px rgba(0,0,0,.4);pointer-events:none}}
.ba-handle::after{{content:'';position:absolute;top:50%;left:50%;width:30px;height:30px;transform:translate(-50%,-50%);
border:2px solid #fff;border-radius:50%;background:rgba(224,85,155,.55)}}
.ba-range{{position:absolute;inset:0;width:100%;height:100%;margin:0;opacity:0;cursor:ew-resize}}
.ba-tag{{position:absolute;bottom:8px;padding:2px 8px;font:600 11px sans-serif;color:#fff;
background:rgba(0,0,0,.55);border-radius:5px;line-height:1.4;pointer-events:none}}
.ba-tag.l{{left:8px}}.ba-tag.r{{right:8px}}
.zoom{{--zoom:3}}
.znav{{position:relative;max-width:520px;margin-bottom:14px;cursor:crosshair;line-height:0}}
.znav img{{width:100%;border-radius:8px;display:block}}
.zbox{{position:absolute;width:calc(100%/var(--zoom));aspect-ratio:var(--asp);
border:2px solid var(--acc);box-shadow:0 0 0 9999px rgba(0,0,0,.28);
transform:translate(-50%,-50%);left:50%;top:50%;pointer-events:none;border-radius:3px}}
.zhint{{position:absolute;left:8px;bottom:8px;font:600 11px sans-serif;color:#fff;
background:rgba(0,0,0,.55);padding:2px 8px;border-radius:5px;line-height:1.4}}
.zrow{{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px}}
.zcell span{{font:600 12px sans-serif;color:var(--mut);display:block;margin-bottom:5px}}
.zview{{width:100%;aspect-ratio:var(--asp);border-radius:8px;border:1px solid var(--line);
background-repeat:no-repeat;background-size:calc(var(--zoom)*100%);background-position:50% 50%;
image-rendering:pixelated}}
</style></head><body>
<h1>sakuro-bench</h1>
<p class=sub>{meta.get('scenario','')} · {meta.get('generated','')}</p>
<ul class=meta>{meta_html}</ul>
{_legend(modes)}
{zoom_html}
<h2>Infographics</h2>
{charts}
{fr_html}
{nr_html}
<h2>Frames — drag the slider (Original ⟷ Enhanced)</h2>
<div class=gallery>{cards}</div>
<script>
document.querySelectorAll('.ba').forEach(function(ba){{
  var over=ba.querySelector('.ba-over'), img=over.querySelector('img'),
      rng=ba.querySelector('.ba-range'), h=ba.querySelector('.ba-handle');
  function sync(){{ img.style.width=ba.clientWidth+'px'; }}
  function set(v){{ over.style.width=v+'%'; h.style.left=v+'%'; }}
  rng.addEventListener('input',function(e){{ set(e.target.value); }});
  new ResizeObserver(sync).observe(ba); sync(); set(rng.value);
}});
(function(){{
  var Z=document.querySelector('.zoom'); if(!Z) return;
  var nav=Z.querySelector('.znav'), box=Z.querySelector('.zbox'),
      views=[].slice.call(Z.querySelectorAll('.zview'));
  function set(x,y){{
    var px=(x*100).toFixed(2)+'%', py=(y*100).toFixed(2)+'%';
    views.forEach(function(v){{ v.style.backgroundPosition=px+' '+py; }});
    box.style.left=px; box.style.top=py;
  }}
  nav.addEventListener('mousemove',function(e){{
    var r=nav.getBoundingClientRect();
    var x=Math.max(0,Math.min(1,(e.clientX-r.left)/r.width));
    var y=Math.max(0,Math.min(1,(e.clientY-r.top)/r.height));
    set(x,y);
  }});
  set(0.5,0.5);
}})();
</script>
</body></html>"""
