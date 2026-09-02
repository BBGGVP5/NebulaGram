#!/usr/bin/env python3
"""Renders the NebulaGram mark into every asset the platforms want.

The mark is defined once here, in a 200x200 space, and everything else is
derived: the square master, the legacy launcher rasters and the foreground layer
of the Android adaptive icon. Nothing has rounding baked in — the launcher
applies its own mask, which is why an icon with a rounded rectangle painted into
the pixels looks wrong on a device that prefers circles or squircles.

    python scripts/gen-icons.py
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OVERLAY = os.path.join(ROOT, "platform", "android", "overlay",
                       "TMessagesProj_AppStandalone", "src", "main", "res")
DESIGN = os.path.join(ROOT, "design", "icon")

# --- the mark, in a 200x200 space -------------------------------------------

# A four-point dart: no interior fold, so the silhouette is its own.
PLANE = [(148, 52), (96, 138), (88, 104), (54, 96)]
PLANE_STROKE = 13  # a stroke in the fill colour is what rounds the corners

# The comet trail: two tapering strokes, given as cubic curves.
TRAILS = [
    (((44, 148), (58, 142), (70, 139), (82, 139)), 9, 218),
    (((40, 124), (49, 120), (57, 118), (65, 118)), 7, 140),
]

GRADIENT_FROM = (60, 141, 240)   # #3C8DF0
GRADIENT_TO = (10, 49, 122)      # #0A317A
INK = (238, 244, 255)            # the dart
TRAIL_INK = (187, 214, 255)

SUPERSAMPLE = 4  # drawn large and shrunk, which is how the edges stay smooth


def bezier(points, steps=48):
    """Samples a cubic curve, so a trail can be drawn as a polyline."""
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = points
    out = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
        y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
        out.append((x, y))
    return out


def gradient(size):
    """A diagonal gradient, drawn per row of the diagonal rather than per pixel."""
    image = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(image)
    span = size * 2
    for step in range(span):
        ratio = step / (span - 1)
        colour = tuple(
            round(GRADIENT_FROM[c] + (GRADIENT_TO[c] - GRADIENT_FROM[c]) * ratio)
            for c in range(3)
        )
        draw.line([(step, 0), (0, step)], fill=colour, width=2)
    return image


def stroke(draw, points, width, colour):
    """Draws a stroke by stamping discs along the path.

    Pillow's own line joints leave notches where segments meet and serrate a
    curve, which is plainly visible at icon sizes. Stamping a disc per step
    gives round caps and joins for free, at the cost of a few thousand cheap
    draw calls on a supersampled canvas.
    """
    radius = width / 2
    if radius < 0.5:
        radius = 0.5
    dense = []
    for i in range(len(points) - 1):
        (x0, y0), (x1, y1) = points[i], points[i + 1]
        span = max(abs(x1 - x0), abs(y1 - y0))
        steps = max(1, int(span / max(0.7, radius / 3)))
        for step in range(steps):
            t = step / steps
            dense.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
    dense.append(points[-1])
    for x, y in dense:
        draw.ellipse([x - radius, y - radius, x + radius, y + radius], fill=colour)


def draw_mark(canvas, scale, offset, opaque=True):
    """Paints the dart and its trail onto canvas, in units of the 200-space."""
    draw = ImageDraw.Draw(canvas)

    def place(point):
        return (point[0] * scale + offset[0], point[1] * scale + offset[1])

    plane = [place(p) for p in PLANE]
    ink = INK + ((255,) if not opaque else ())
    draw.polygon(plane, fill=ink)
    # The corners are rounded by a stroke in the fill colour, the same trick the
    # SVG uses, so the silhouette matches on every platform.
    stroke(draw, plane + [plane[0]], PLANE_STROKE * scale, ink)

    for curve, width, alpha in TRAILS:
        points = [place(p) for p in bezier(curve)]
        colour = TRAIL_INK + ((alpha,) if not opaque else ())
        stroke(draw, points, width * scale, colour)


def square_icon(size):
    """The full-bleed square: gradient plus mark, no rounding of any kind."""
    big = size * SUPERSAMPLE
    canvas = gradient(big).convert("RGBA")
    # The dart sits on 74% of the tile, which keeps it clear of a circular mask.
    scale = big / 200 * 0.74
    inset = (big - 200 * scale) / 2
    draw_mark(canvas, scale, (inset, inset))
    return canvas.resize((size, size), Image.LANCZOS).convert("RGB")


def foreground_layer(size):
    """The adaptive foreground: transparent, content inside the 66% safe zone."""
    big = size * SUPERSAMPLE
    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    scale = big / 200 * 0.52
    inset = (big - 200 * scale) / 2
    draw_mark(canvas, scale, (inset, inset), opaque=False)
    return canvas.resize((size, size), Image.LANCZOS)


def rounded(image, radius_ratio=0.225):
    """A rounded copy, for places that show the icon as a picture.

    The app icon itself must stay square — the launcher masks it — but a README
    or a web page draws the file as-is, where a bare square reads as unfinished.
    The ratio matches what Android and iOS apply to a launcher tile.
    """
    size = image.size[0]
    scale = 4  # mask drawn large and shrunk, so the curve has no stairsteps
    mask = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size * scale - 1, size * scale - 1],
        radius=int(size * scale * radius_ratio), fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)

    out = image.convert("RGBA")
    out.putalpha(mask)
    return out


def write(path, image):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print("  ", os.path.relpath(path, ROOT))


def main():
    # Density buckets: legacy launcher icons are 48dp, adaptive layers 108dp.
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

    print("master:")
    master = square_icon(1024)
    write(os.path.join(DESIGN, "icon-1024.png"), master)
    # Same artwork, rounded, for the README and anywhere else it is shown as a
    # picture rather than installed as an icon.
    write(os.path.join(DESIGN, "icon-rounded.png"),
          rounded(master.resize((512, 512), Image.LANCZOS)))

    print("legacy launcher (pre-Android 8):")
    for bucket, factor in densities.items():
        write(os.path.join(OVERLAY, f"mipmap-{bucket}", "ic_launcher_sa.png"),
              square_icon(round(48 * factor)))

    print("adaptive foreground:")
    for bucket, factor in densities.items():
        write(os.path.join(OVERLAY, f"mipmap-{bucket}", "nebula_icon_foreground.png"),
              foreground_layer(round(108 * factor)))


if __name__ == "__main__":
    main()
