#!/usr/bin/env python3
"""Generate the Sakuro app icon from one shared geometry.

The mark is an open "eclipse" ring around a rounded play triangle, with a
sakura blossom sitting in the ring's gap. Every output is derived from the same
shapes, so the full-color, monochrome (Android 13 themed) and SVG master
versions never drift apart.

Outputs:
  composeApp/src/androidMain/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
  assets/branding/sakuro-app-icon.svg             (full color, store/README master)
  assets/branding/sakuro-app-icon-monochrome.svg  (single-color mark, transparent)

Coordinates use the 108x108 adaptive-icon viewport. The foreground mark stays
inside the 66-unit safe circle so no launcher mask clips it.

Usage: python3 tools/branding/generate_app_icon.py
"""

from __future__ import annotations

import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "composeApp/src/androidMain/res/drawable"
BRANDING = ROOT / "assets/branding"

# Palette (BRAND.md §2).
BACKGROUND = "#120A17"
SURFACE = "#1E1226"
TWILIGHT = "#4A2A5A"
ACCENT_SAKURA = "#EC8FC0"
ACCENT_LAVENDER = "#A57FD6"
GLOW_MAGENTA = "#C94F9C"
CHROME = ("#F6D9E6", "#E79ECB", "#B98CD9")  # logo metallic gradient

CX = CY = 54.0
RING_RADIUS = 24.0
RING_STROKE = 3.6
BLOSSOM_ANGLE = -45.0  # screen degrees, y down: up-right of the ring
BLOSSOM_RADIUS = 8.4
RING_GAP = 31.0  # half-angle of the ring opening around the blossom
PLAY_HEIGHT = 21.0
PLAY_CORNER = 2.4


def fmt(value: float) -> str:
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def polar(cx: float, cy: float, radius: float, degrees: float) -> tuple[float, float]:
    rad = math.radians(degrees)
    return cx + radius * math.cos(rad), cy + radius * math.sin(rad)


def ring_arc(radius: float, gap_center: float, gap_half: float) -> str:
    """Open circular arc that leaves a gap of +-gap_half degrees around gap_center."""
    start = polar(CX, CY, radius, gap_center + gap_half)
    end = polar(CX, CY, radius, gap_center - gap_half)
    large = 1 if 360 - 2 * gap_half > 180 else 0
    return (
        f"M{fmt(start[0])},{fmt(start[1])}"
        f"A{fmt(radius)},{fmt(radius)} 0 {large} 1 {fmt(end[0])},{fmt(end[1])}"
    )


def partial_arc(radius: float, from_deg: float, to_deg: float) -> str:
    start = polar(CX, CY, radius, from_deg)
    end = polar(CX, CY, radius, to_deg)
    large = 1 if (to_deg - from_deg) % 360 > 180 else 0
    return (
        f"M{fmt(start[0])},{fmt(start[1])}"
        f"A{fmt(radius)},{fmt(radius)} 0 {large} 1 {fmt(end[0])},{fmt(end[1])}"
    )


def petal(cx: float, cy: float, length: float, degrees: float, base: float = 0.9) -> str:
    """A sakura petal: teardrop with the characteristic notch at its tip."""
    width = length * 0.41
    local = [
        ("M", [(base, 0)]),
        ("C", [(base + length * 0.22, -width * 1.15), (length * 0.86, -width * 1.08), (length, -width * 0.36)]),
        ("L", [(length * 0.84, 0)]),
        ("L", [(length, width * 0.36)]),
        ("C", [(length * 0.86, width * 1.08), (base + length * 0.22, width * 1.15), (base, 0)]),
    ]
    rad = math.radians(degrees)
    cos, sin = math.cos(rad), math.sin(rad)
    out = []
    for cmd, points in local:
        coords = []
        for x, y in points:
            gx = cx + x * cos - y * sin
            gy = cy + x * sin + y * cos
            coords.append(f"{fmt(gx)},{fmt(gy)}")
        out.append(cmd + " ".join(coords))
    return "".join(out) + "Z"


def blossom_petals(cx: float, cy: float, length: float, facing: float) -> list[str]:
    return [petal(cx, cy, length, facing + k * 72) for k in range(5)]


def rounded_polygon(points: list[tuple[float, float]], corner: float) -> str:
    """Polygon with each vertex filleted by a quadratic curve."""
    n = len(points)
    cut = []
    for i, (px, py) in enumerate(points):
        prev_x, prev_y = points[i - 1]
        next_x, next_y = points[(i + 1) % n]
        to_prev = math.hypot(prev_x - px, prev_y - py)
        to_next = math.hypot(next_x - px, next_y - py)
        a = (px + (prev_x - px) * corner / to_prev, py + (prev_y - py) * corner / to_prev)
        b = (px + (next_x - px) * corner / to_next, py + (next_y - py) * corner / to_next)
        cut.append((a, (px, py), b))
    path = f"M{fmt(cut[0][2][0])},{fmt(cut[0][2][1])}"
    for i in range(1, n + 1):
        a, v, b = cut[i % n]
        path += f"L{fmt(a[0])},{fmt(a[1])}Q{fmt(v[0])},{fmt(v[1])} {fmt(b[0])},{fmt(b[1])}"
    return path + "Z"


def play_triangle(height: float, corner: float) -> str:
    width = height * math.sqrt(3) / 2
    left = CX - width / 3  # centroid on the ring center
    return rounded_polygon(
        [(left, CY - height / 2), (left + width, CY), (left, CY + height / 2)],
        corner,
    )


def circle(cx: float, cy: float, r: float) -> str:
    return (
        f"M{fmt(cx - r)},{fmt(cy)}"
        f"a{fmt(r)},{fmt(r)} 0 1 1 {fmt(2 * r)},0"
        f"a{fmt(r)},{fmt(r)} 0 1 1 {fmt(-2 * r)},0Z"
    )


BLOSSOM = polar(CX, CY, RING_RADIUS, BLOSSOM_ANGLE)
RING_PATH = ring_arc(RING_RADIUS, BLOSSOM_ANGLE, RING_GAP)
HAIRLINE_PATH = partial_arc(RING_RADIUS - 3.6, 110, 215)
PETALS = blossom_petals(*BLOSSOM, BLOSSOM_RADIUS, BLOSSOM_ANGLE)
PLAY_PATH = play_triangle(PLAY_HEIGHT, PLAY_CORNER)
# Drifting petals in the background: (x, y, length, rotation, alpha).
FALLING = [
    (30.5, 30.0, 3.4, 200, 0.55),
    (82.0, 70.5, 3.0, 150, 0.45),
    (25.0, 74.0, 2.6, 110, 0.35),
    (77.0, 84.0, 2.2, 60, 0.3),
]
WATERMARK = blossom_petals(20.0, 92.0, 22.0, -60)


# ---------------------------------------------------------------- shapes ----
# Each shape is (path, fill, extras). fill is either a hex color, or a gradient
# dict. Shapes are emitted to both SVG and Android vector XML.

def linear(x1, y1, x2, y2, stops):
    return {"type": "linear", "coords": (x1, y1, x2, y2), "stops": stops}


def radial(cx, cy, r, stops):
    return {"type": "radial", "coords": (cx, cy, r), "stops": stops}


def background_shapes():
    shapes = [
        {"d": "M0,0h108v108h-108z", "fill": radial(54, 46, 62, [(0, TWILIGHT), (0.5, SURFACE), (1, BACKGROUND)])},
    ]
    for d in WATERMARK:
        shapes.append({"d": d, "fill": ACCENT_LAVENDER, "alpha": 0.06})
    shapes.append({"d": circle(*BLOSSOM, 18), "fill": radial(*BLOSSOM, 18, [(0, "#55EC8FC0"), (1, "#00EC8FC0")])})
    shapes.append({"d": circle(CX, CY, 24), "fill": radial(CX, CY, 24, [(0, "#40C94F9C"), (1, "#00C94F9C")])})
    for x, y, length, rot, alpha in FALLING:
        shapes.append({"d": petal(x, y, length, rot, base=0), "fill": ACCENT_SAKURA, "alpha": alpha})
    return shapes


def foreground_shapes():
    ring_gradient = linear(30, 30, 80, 80, [(0, CHROME[0]), (0.5, CHROME[1]), (1, CHROME[2])])
    petal_gradient = linear(BLOSSOM[0] - 6, BLOSSOM[1] + 6, BLOSSOM[0] + 6, BLOSSOM[1] - 6,
                            [(0, "#E070AE"), (0.55, ACCENT_SAKURA), (1, "#FFE6F2")])
    shapes = [
        {"d": HAIRLINE_PATH, "stroke": "#FFFFFF", "width": 0.5, "alpha": 0.35},
        {"d": RING_PATH, "stroke": ring_gradient, "width": RING_STROKE},
        {"d": PLAY_PATH, "fill": linear(46, 44, 66, 64, [(0, "#FFFFFF"), (1, CHROME[0])])},
    ]
    shapes += [{"d": d, "fill": petal_gradient} for d in PETALS]
    shapes.append({"d": circle(*BLOSSOM, 1.6), "fill": GLOW_MAGENTA})
    return shapes


def monochrome_shapes():
    shapes = [
        {"d": RING_PATH, "stroke": "#FFFFFF", "width": RING_STROKE + 0.4},
        {"d": PLAY_PATH, "fill": "#FFFFFF"},
    ]
    shapes += [{"d": d, "fill": "#FFFFFF"} for d in PETALS]
    return shapes


# ------------------------------------------------------------ android xml ---

def android_color(color: str, alpha: float = 1.0) -> str:
    color = color.upper()
    if len(color) == 9:
        return color
    return f"#{round(alpha * 255):02X}{color[1:]}"


def android_gradient(attr: str, grad: dict) -> str:
    if grad["type"] == "linear":
        x1, y1, x2, y2 = grad["coords"]
        head = (f'android:type="linear" android:startX="{fmt(x1)}" android:startY="{fmt(y1)}" '
                f'android:endX="{fmt(x2)}" android:endY="{fmt(y2)}"')
    else:
        cx, cy, r = grad["coords"]
        head = (f'android:type="radial" android:centerX="{fmt(cx)}" android:centerY="{fmt(cy)}" '
                f'android:gradientRadius="{fmt(r)}"')
    items = "\n".join(
        f'                <item android:offset="{fmt(o)}" android:color="{android_color(c)}" />'
        for o, c in grad["stops"]
    )
    return (f'        <aapt:attr name="android:{attr}">\n'
            f"            <gradient {head}>\n{items}\n            </gradient>\n"
            f"        </aapt:attr>\n")


def android_path(shape: dict) -> str:
    attrs = [f'android:pathData="{shape["d"]}"']
    children = ""
    alpha = shape.get("alpha", 1.0)
    if "stroke" in shape:
        attrs += [f'android:strokeWidth="{fmt(shape["width"])}"', 'android:strokeLineCap="round"']
        if isinstance(shape["stroke"], dict):
            children += android_gradient("strokeColor", shape["stroke"])
        else:
            attrs.append(f'android:strokeColor="{android_color(shape["stroke"], alpha)}"')
    else:
        if isinstance(shape["fill"], dict):
            children += android_gradient("fillColor", shape["fill"])
        else:
            attrs.append(f'android:fillColor="{android_color(shape["fill"], alpha)}"')
    joined = "\n        ".join(attrs)
    if children:
        return f"    <path\n        {joined}>\n{children}    </path>\n"
    return f"    <path\n        {joined} />\n"


def android_vector(comment: str, shapes: list[dict]) -> str:
    uses_aapt = any(isinstance(s.get("fill", s.get("stroke")), dict) for s in shapes)
    ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if uses_aapt else ""
    body = "\n".join(android_path(s) for s in shapes)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {comment}\n     Generated by tools/branding/generate_app_icon.py; edit the script, not this file. -->\n"
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n\n'
        f"{body}</vector>\n"
    )


# ------------------------------------------------------------------- svg ----

def svg_color(color: str) -> tuple[str, float]:
    if len(color) == 9:  # #AARRGGBB
        return "#" + color[3:], int(color[1:3], 16) / 255
    return color, 1.0


def svg_document(view_box: str, size: int, layers: list[list[dict]]) -> str:
    defs, body = [], []
    for index, shape in enumerate(s for layer in layers for s in layer):
        paint = shape.get("stroke", shape.get("fill"))
        if isinstance(paint, dict):
            gid = f"g{index}"
            stops = "".join(
                '<stop offset="{}" stop-color="{}" stop-opacity="{}"/>'.format(fmt(o), *map(
                    lambda v: fmt(v) if isinstance(v, float) else v, svg_color(c)))
                for o, c in paint["stops"]
            )
            if paint["type"] == "linear":
                x1, y1, x2, y2 = map(fmt, paint["coords"])
                defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                            f'x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{stops}</linearGradient>')
            else:
                cx, cy, r = map(fmt, paint["coords"])
                defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                            f'cx="{cx}" cy="{cy}" r="{r}">{stops}</radialGradient>')
            paint_ref = f"url(#{gid})"
        else:
            paint_ref = paint
        opacity = f' opacity="{fmt(shape["alpha"])}"' if shape.get("alpha", 1.0) != 1.0 else ""
        if "stroke" in shape:
            body.append(f'<path d="{shape["d"]}" fill="none" stroke="{paint_ref}" '
                        f'stroke-width="{fmt(shape["width"])}" stroke-linecap="round"{opacity}/>')
        else:
            body.append(f'<path d="{shape["d"]}" fill="{paint_ref}"{opacity}/>')
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view_box}">\n'
        f"<!-- Generated by tools/branding/generate_app_icon.py -->\n"
        f"<defs>{''.join(defs)}</defs>\n" + "\n".join(body) + "\n</svg>\n"
    )


def main() -> None:
    outputs = {
        RES / "ic_launcher_background.xml": android_vector(
            "Adaptive launcher background: twilight plum with drifting sakura petals.", background_shapes()),
        RES / "ic_launcher_foreground.xml": android_vector(
            "Adaptive launcher foreground: eclipse ring, play triangle and sakura blossom.", foreground_shapes()),
        RES / "ic_launcher_monochrome.xml": android_vector(
            "Android 13+ themed icon mask. The launcher supplies the final color.", monochrome_shapes()),
        # The masters show the 72-unit visible area of the adaptive icon.
        BRANDING / "sakuro-app-icon.svg": svg_document(
            "18 18 72 72", 512, [background_shapes(), foreground_shapes()]),
        BRANDING / "sakuro-app-icon-monochrome.svg": svg_document(
            "18 18 72 72", 512, [monochrome_shapes()]),
    }
    for path, content in outputs.items():
        path.write_text(content, encoding="utf-8")
        print(f"wrote {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
