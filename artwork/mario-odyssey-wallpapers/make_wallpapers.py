#!/usr/bin/env python3
"""Build wallpapers.html: eight 1920x1080 Super Mario Odyssey kingdom wallpapers for the TV launcher.

Usage: python3 -I make_wallpapers.py [scenes.json]

Each wallpaper is one <section> of the page. Skies, silhouettes and glows are SVG drawn here from the
kingdom's palette and motif parameters in scenes.json; one character PNG from
../mario-odyssey-icons/characters/ stands in the right part of the frame, never larger than its
native size. The legibility shade (dark bottom band, soft shadow behind the clock) and the film
grain are part of the page, so render_wallpapers.mjs only takes screenshots. Random details use the
fixed seeds in scenes.json: the same scenes.json always writes the same HTML.

The launcher draws over the wallpaper, so every character must stay inside character_zone: its top
below the top-right buttons (min_top) and the bottom of its face (the "face" share of its height:
chin, jaw or lowest eye) above face_line, where the near-opaque dock band starts with four grid columns.

Open wallpapers.html#guides to outline where the launcher's clock, buttons, title and dock sit, and
where each character's face ends.
"""
from __future__ import annotations

import hashlib
import html
import json
import math
import random
import re
import struct
import sys
from pathlib import Path


HERE = Path(__file__).resolve().parent
DEFAULT_SCENES = HERE / "scenes.json"
ICONS = HERE.parent / "mario-odyssey-icons"
W, H = 1920, 1080
HEX = re.compile(r"#[0-9A-F]{6}")
SCENE_ID = re.compile(r"[0-9]{2}-[a-z]+")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
CHARACTER_FIELDS = {
    "id": str, "name": dict, "center_x": (int, float), "top": (int, float), "height": (int, float),
    "face": (int, float), "flip": bool, "fade": (int, float), "brightness": (int, float), "saturate": (int, float),
    "glow": str, "glow_alpha": (int, float), "backlight": (int, float), "tint": (int, float), "shadow": bool,
}


# ---- scenes.json ------------------------------------------------------------------------------

def need(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def is_number(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def check_palette(where: str, palette: object) -> None:
    need(isinstance(palette, dict) and bool(palette), f"{where}: palette must be a non-empty object")
    for key, value in palette.items():
        if key == "sky":
            need(isinstance(value, list) and len(value) >= 2 and all(
                isinstance(stop, list) and len(stop) == 2 and is_number(stop[0]) and 0 <= stop[0] <= 1
                and isinstance(stop[1], str) and HEX.fullmatch(stop[1]) for stop in value),
                f"{where}: sky must be a list of [offset, #RRGGBB] stops")
        elif isinstance(value, list):
            need(all(isinstance(c, str) and HEX.fullmatch(c) for c in value), f"{where}: {key} must hold #RRGGBB colours")
        else:
            need(isinstance(value, str) and HEX.fullmatch(value) is not None, f"{where}: {key} must be #RRGGBB")


def check_character(where: str, ch: object, sources: dict, zone: dict) -> dict:
    """Validate one placement and attach its box; the source PNG must match sources.json byte for byte."""
    need(isinstance(ch, dict) and set(ch) == set(CHARACTER_FIELDS), f"{where}: character needs exactly {sorted(CHARACTER_FIELDS)}")
    for key, kind in CHARACTER_FIELDS.items():
        value = ch[key]
        need(isinstance(value, kind) and (kind is bool or not isinstance(value, bool)), f"{where}: {key} has the wrong type")
    need(isinstance(ch["name"].get("zh"), str) and isinstance(ch["name"].get("en"), str), f"{where}: name needs zh and en")
    need(HEX.fullmatch(ch["glow"]) is not None, f"{where}: glow must be #RRGGBB")
    for key in ("fade", "glow_alpha", "backlight", "tint"):
        need(0 <= ch[key] <= 1, f"{where}: {key} must be between 0 and 1")
    need(0 < ch["face"] <= 1, f"{where}: face must be a share of the height, above 0 and at most 1")
    for key in ("brightness", "saturate"):
        need(0.5 <= ch[key] <= 1.2, f"{where}: {key} must be between 0.5 and 1.2")
    source = sources.get(ch["id"])
    need(source is not None, f"{where}: {ch['id']} is not in ../mario-odyssey-icons/sources.json")
    png = ICONS / source["file"]
    data = png.read_bytes()
    need(hashlib.sha256(data).hexdigest() == source["sha256"], f"{png} differs from sources.json")
    need(data[:8] == PNG_SIGNATURE and struct.unpack(">II", data[16:24]) == (source["width"], source["height"]),
         f"{png} is not the {source['width']}x{source['height']} PNG listed in sources.json")
    height = ch["height"]
    # Characters are only ever scaled down: a larger copy would add blur, not detail.
    need(height <= source["height"], f"{where}: {ch['id']} at {height}px would upscale its {source['height']}px source")
    need(height <= zone["max_height"], f"{where}: {ch['id']} is taller than {zone['max_height']}px")
    width = height * source["width"] / source["height"]
    left = ch["center_x"] - width / 2
    need(left >= zone["min_left"] and left + width <= zone["max_right"] and ch["top"] >= zone["min_top"],
         f"{where}: {ch['id']} leaves the character zone (x {left:.0f}-{left + width:.0f}, top {ch['top']})")
    # The body may run on behind the dock band; the face must not be cut by it.
    face_y = ch["top"] + height * ch["face"]
    need(face_y <= zone["face_line"], f"{where}: {ch['id']}'s face ends at y {face_y:.0f}, below face_line {zone['face_line']}")
    return {**ch, "src": "../mario-odyssey-icons/" + source["file"], "native": [source["width"], source["height"]],
            "box": [round(left, 1), ch["top"], round(width, 1), height], "face_y": round(face_y, 1)}


def load_scenes(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    need(isinstance(value, dict) and isinstance(value.get("scenes"), list), f"{path} must be an object with a scenes list")
    need(value.get("canvas") == {"width": W, "height": H}, f"{path}: canvas must be {W}x{H}")
    need(isinstance(value.get("output"), str) and value["output"], f"{path}: output must name the JPEG folder")
    need(isinstance(value.get("jpeg_quality"), int) and 60 <= value["jpeg_quality"] <= 95, f"{path}: jpeg_quality must be 60-95")
    zone = value.get("character_zone")
    need(isinstance(zone, dict) and all(is_number(zone.get(k)) for k in ("min_left", "max_right", "min_top", "max_height", "face_line")),
         f"{path}: character_zone needs min_left, max_right, min_top, max_height and face_line")
    need(zone["min_top"] < zone["face_line"], f"{path}: character_zone face_line must lie below min_top")
    shade = value.get("legibility")
    need(isinstance(shade, dict) and HEX.fullmatch(str(shade.get("color"))) is not None
         and isinstance(shade.get("bottom"), list) and isinstance(shade.get("clock"), dict) and isinstance(shade.get("top"), dict),
         f"{path}: legibility needs color, bottom, clock and top")
    grain = value.get("grain")
    need(isinstance(grain, dict) and all(is_number(grain.get(k)) for k in ("strength", "frequency", "octaves", "seed")),
         f"{path}: grain needs strength, frequency, octaves and seed")
    need(isinstance(value.get("guides"), list), f"{path}: guides must be a list")
    sources = {x["id"]: x for x in json.loads((ICONS / "sources.json").read_text(encoding="utf-8"))}
    seen = set()
    scenes = []
    for scene in value["scenes"]:
        need(isinstance(scene, dict), f"{path}: every scene must be an object")
        sid = scene.get("id")
        need(isinstance(sid, str) and SCENE_ID.fullmatch(sid) is not None and sid not in seen, f"{path}: bad or repeated id {sid!r}")
        seen.add(sid)
        title = scene.get("title")
        need(isinstance(title, dict) and isinstance(title.get("zh"), str) and isinstance(title.get("en"), str), f"{sid}: title needs zh and en")
        need(isinstance(scene.get("mood"), str), f"{sid}: mood must be a string")
        check_palette(sid, scene.get("palette"))
        motif = scene.get("motif")
        need(isinstance(motif, dict) and motif.get("type") in MOTIFS and isinstance(motif.get("seed"), int),
             f"{sid}: motif needs a known type ({', '.join(MOTIFS)}) and an integer seed")
        need(isinstance(scene.get("characters"), list) and scene["characters"], f"{sid}: characters must be a non-empty list")
        chars = [check_character(f"{sid}/{i}", ch, sources, zone) for i, ch in enumerate(scene["characters"])]
        scenes.append({**scene, "characters": chars})
    need(len(scenes) == 8, f"{path}: expected 8 scenes, found {len(scenes)}")
    return {**value, "scenes": scenes}


# ---- SVG building blocks ----------------------------------------------------------------------

def rgb(color: str, alpha: float) -> str:
    r, g, b = (int(color[i:i + 2], 16) for i in (1, 3, 5))
    return f"rgb({r} {g} {b} / {alpha:.3f})"


def stops_svg(stops) -> str:
    return "".join(f'<stop offset="{o:g}" stop-color="{c}"' + (f' stop-opacity="{a:.3f}"' if a != 1 else "") + "/>"
                   for o, c, a in stops)


class Canvas:
    """One scene's SVG. Ids carry the scene prefix: every inline SVG shares the page's id space."""

    def __init__(self, prefix: str, seed: int):
        self.prefix = prefix
        self.rnd = random.Random(seed)
        self.defs: list[str] = []
        self.back: list[str] = []
        self.front: list[str] = []
        self.cast: list[str] = []
        self.blurs: set[str] = set()
        self.count = 0

    def new_id(self, kind: str) -> str:
        self.count += 1
        return f"{self.prefix}-{kind}{self.count}"

    def linear(self, stops, x1=0.0, y1=0.0, x2=0.0, y2=1.0, user=False) -> str:
        """A linear gradient fill; stops are (offset, colour, opacity). user=True takes page coordinates."""
        gid = self.new_id("l")
        units = ' gradientUnits="userSpaceOnUse"' if user else ""
        self.defs.append(f'<linearGradient id="{gid}" x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}"{units}>'
                         f"{stops_svg(stops)}</linearGradient>")
        return f"url(#{gid})"

    def radial(self, stops, cx=0.5, cy=0.5, r=0.5) -> str:
        gid = self.new_id("r")
        self.defs.append(f'<radialGradient id="{gid}" cx="{cx:g}" cy="{cy:g}" r="{r:g}">{stops_svg(stops)}</radialGradient>')
        return f"url(#{gid})"

    def blur(self, amount: float) -> str:
        """A Gaussian blur filter attribute, computed in sRGB so dark glows keep their 8-bit steps small."""
        fid = f"{self.prefix}-b{amount:g}".replace(".", "_")
        if fid not in self.blurs:
            self.blurs.add(fid)
            self.defs.append(f'<filter id="{fid}" x="-60%" y="-60%" width="220%" height="220%" '
                             f'color-interpolation-filters="sRGB"><feGaussianBlur stdDeviation="{amount:g}"/></filter>')
        return f'filter="url(#{fid})"'

    def svg(self) -> str:
        return (f'<svg class="scene" viewBox="0 0 {W} {H}" width="{W}" height="{H}" aria-hidden="true">'
                f'<defs>{"".join(self.defs)}</defs>{"".join(self.back)}{"".join(self.cast)}{"".join(self.front)}</svg>')


def sky(c: Canvas, pal: dict) -> None:
    c.back.append(f'<rect width="{W}" height="{H}" fill="{c.linear([(o, col, 1) for o, col in pal["sky"]])}"/>')


def glow(c: Canvas, cx, cy, rx, ry, color, alpha, layer="back", falloff=0.42) -> None:
    """A soft light: the colour at the centre, fading to nothing at the ellipse edge."""
    fill = c.radial([(0, color, alpha), (falloff, color, alpha * 0.45), (1, color, 0)])
    getattr(c, layer).append(f'<ellipse cx="{cx:.1f}" cy="{cy:.1f}" rx="{rx:.1f}" ry="{ry:.1f}" fill="{fill}"/>')


def stars(c: Canvas, count, box, color, size=(0.5, 1.4), alpha=(0.15, 0.7), fade=0.75) -> None:
    """Small points, fainter towards the bottom of the box; one in twenty gets a soft halo."""
    x0, y0, x1, y1 = box
    dots, halos = [], []
    for _ in range(count):
        x, y = c.rnd.uniform(x0, x1), c.rnd.uniform(y0, y1)
        a = c.rnd.uniform(*alpha) * (1 - fade * (y - y0) / max(1, y1 - y0))
        r = c.rnd.uniform(*size)
        dots.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.2f}" fill-opacity="{a:.2f}"/>')
        if c.rnd.random() < 0.05:
            halos.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r * 3.2:.1f}" fill-opacity="{a * 0.45:.2f}"/>')
    c.back.append(f'<g fill="{color}" {c.blur(2)}>{"".join(halos)}</g><g fill="{color}">{"".join(dots)}</g>')


def wave(rnd: random.Random, base, amp, waves=1.5, step=16, rough=0.0) -> list[tuple[float, float]]:
    """A gentle skyline: three summed sines across the width."""
    phases = [rnd.uniform(0, math.tau) for _ in range(3)]
    pts = []
    for x in range(-step, W + 2 * step, step):
        t = x / W * math.tau * waves
        y = base - amp * (0.62 * math.sin(t + phases[0]) + 0.28 * math.sin(2.3 * t + phases[1])
                          + 0.1 * math.sin(5.1 * t + phases[2]))
        pts.append((x, y + (rnd.uniform(-rough, rough) if rough else 0)))
    return pts


def area(pts, bottom=H) -> str:
    return ("M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts)
            + f" L{pts[-1][0]:.1f},{bottom} L{pts[0][0]:.1f},{bottom} Z")


def polyline(pts) -> str:
    return "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts)


def smooth(pts) -> str:
    """A Catmull-Rom curve through the points, as cubic Beziers."""
    d = f"M{pts[0][0]:.1f},{pts[0][1]:.1f}"
    for i in range(len(pts) - 1):
        p0, p1, p2 = pts[max(0, i - 1)], pts[i], pts[i + 1]
        p3 = pts[min(len(pts) - 1, i + 2)]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f" C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}"
    return d


def at(pts, x) -> float:
    """The y of a wave at x."""
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        if xa <= x <= xb:
            return ya + (yb - ya) * (x - xa) / (xb - xa)
    return pts[-1][1]


def mist(c: Canvas, color, bands, layer="back", blur=26) -> None:
    """Horizontal veils of fog: (cx, cy, rx, ry, opacity) each."""
    shapes = "".join(f'<ellipse cx="{x:.1f}" cy="{y:.1f}" rx="{rx:.1f}" ry="{ry:.1f}" fill-opacity="{a:.3f}"/>'
                     for x, y, rx, ry, a in bands)
    getattr(c, layer).append(f'<g fill="{color}" {c.blur(blur)}>{shapes}</g>')


def leaf(cx, cy, angle, length, width, droop) -> str:
    """A leaf or palm frond from (cx, cy): two quadratic edges meeting at a drooping tip."""
    a = math.radians(angle)
    dx, dy, nx, ny = math.cos(a), math.sin(a), -math.sin(a), math.cos(a)
    tx, ty = cx + dx * length, cy + dy * length + droop
    mx, my = cx + dx * length * 0.55, cy + dy * length * 0.55 + droop * 0.25
    return (f"M{cx:.1f},{cy:.1f} Q{mx + nx * width:.1f},{my + ny * width:.1f} {tx:.1f},{ty:.1f} "
            f"Q{mx - nx * width * 0.55:.1f},{my - ny * width * 0.55:.1f} {cx:.1f},{cy:.1f} Z")


# ---- kingdoms ---------------------------------------------------------------------------------

def top_hat(rnd, x, base, w, h, tilt, fill, band, window, lit, window_alpha):
    """A Bonneton house: a top hat with an upturned brim, a band and a few round-topped windows."""
    ry = max(3.0, 0.1 * w)
    crown = (f"M{x - .44 * w:.1f},{base:.1f} L{x - .5 * w:.1f},{base - h:.1f} "
             f"Q{x:.1f},{base - h - .12 * w:.1f} {x + .5 * w:.1f},{base - h:.1f} L{x + .44 * w:.1f},{base:.1f} Z")
    lift = base - 0.5 * ry - 0.1 * w
    brim = (f"M{x - .82 * w:.1f},{lift:.1f} Q{x - .7 * w:.1f},{base + ry:.1f} {x:.1f},{base + ry:.1f} "
            f"Q{x + .7 * w:.1f},{base + ry:.1f} {x + .82 * w:.1f},{lift:.1f} Q{x:.1f},{base - .3 * ry:.1f} {x - .82 * w:.1f},{lift:.1f} Z")
    band_path = (f"M{x - .475 * w:.1f},{base - .34 * h:.1f} L{x + .475 * w:.1f},{base - .34 * h:.1f} "
                 f"L{x + .46 * w:.1f},{base - .2 * h:.1f} L{x - .46 * w:.1f},{base - .2 * h:.1f} Z")
    lights = []
    # Windows sit at staggered heights: a level pair under a hat brim reads as a pair of eyes.
    slots = [(-0.16, 0.66), (0.2, 0.32), (0.02, 0.86)][:1 if w < 70 else rnd.choice([1, 1, 2])]
    for dx, dy in slots:
        if rnd.random() < lit:
            ww, wh = 0.1 * w, 0.17 * h
            lights.append(f'<rect x="{x + dx * w - ww / 2:.1f}" y="{base - dy * h - wh:.1f}" width="{ww:.1f}" height="{wh:.1f}" rx="{ww / 2:.1f}" '
                          f'fill-opacity="{window_alpha * rnd.uniform(.6, 1):.2f}"/>')
    transform = f'transform="rotate({tilt:.1f} {x:.1f} {base:.1f})"'
    body = f'<g {transform} fill="{fill}"><path d="{crown}"/><path d="{brim}"/><path d="{band_path}" fill="{band}"/></g>'
    return body, (f'<g {transform} fill="{window}">{"".join(lights)}</g>' if lights else "")


def cap(c: Canvas, pal: dict, m: dict) -> None:
    sky(c, pal)
    mx, my, mr = m["moon"]
    hz = m["horizon"]
    stars(c, m["stars"], (0, 0, W, hz - 260), pal["star"], (0.5, 1.2), (0.12, 0.5))
    glow(c, mx, my, mr * 6.5, mr * 6.5, pal["moon"], 0.13)
    c.back.append(f'<circle cx="{mx}" cy="{my}" r="{mr}" fill="{pal["moon"]}" fill-opacity=".8"/>'
                  f'<g fill="{pal["far"]}" fill-opacity=".14"><circle cx="{mx - mr * .3:.1f}" cy="{my - mr * .2:.1f}" r="{mr * .28:.1f}"/>'
                  f'<circle cx="{mx + mr * .25:.1f}" cy="{my + mr * .3:.1f}" r="{mr * .2:.1f}"/></g>')
    mist(c, pal["mist"], [(mx - 40, my + mr * .55, 360, 14, .3), (mx + 220, my + mr * 1.6, 420, 18, .16),
                          (460, 330, 520, 22, .07), (1300, 420, 620, 26, .08)])
    glow(c, 960, hz - 30, 1350, 190, pal["mist"], 0.2)
    bodies, lights = [], []
    x = -30.0
    while x < W + 60:
        w = c.rnd.uniform(38, 82)
        body, light = top_hat(c.rnd, x, hz + c.rnd.uniform(-12, 10), w, w * c.rnd.uniform(0.75, 1.35), c.rnd.uniform(-8, 8),
                              pal["far"], pal["far_band"], pal["window"], m["lit_windows"] * 0.7, 0.55)
        bodies.append(body)
        lights.append(light)
        x += w * c.rnd.uniform(1.25, 1.9)
    tx, tbase, tw, th = m["tower"]
    body, light = top_hat(c.rnd, tx, tbase, tw, th, -2, pal["far"], pal["far_band"], pal["window"], 1, 0.75)
    bodies.append(body)
    lights.append(light)
    body, light = top_hat(c.rnd, tx + 8, tbase - th - 0.05 * tw, tw * 0.6, th * 0.45, 5, pal["far"], pal["far_band"], pal["window"], 1, 0.7)
    bodies.append(body)
    lights.append(light)
    c.back.append("".join(bodies))
    c.back.append(f'<g {c.blur(5)}>{"".join(lights)}</g>{"".join(lights)}')
    mist(c, pal["mist"], [(x, hz + c.rnd.uniform(10, 60), c.rnd.uniform(260, 460), c.rnd.uniform(18, 30), c.rnd.uniform(.12, .2))
                          for x in range(0, W + 200, 240)], blur=30)
    bodies, lights = [], []
    x = -60.0
    while x < W + 80:
        w = c.rnd.uniform(110, 200)
        body, light = top_hat(c.rnd, x, hz + c.rnd.uniform(200, 270), w, w * c.rnd.uniform(0.7, 1.1), c.rnd.uniform(-6, 6),
                              pal["near"], pal["near_band"], pal["window"], m["lit_windows"], 0.85)
        bodies.append(body)
        lights.append(light)
        x += w * c.rnd.uniform(1.4, 2.0)
    c.front.append("".join(bodies))
    c.front.append(f'<g {c.blur(6)}>{"".join(lights)}</g>{"".join(lights)}')
    mist(c, pal["mist"], [(1540, hz + 60, 480, 40, .14), (700, hz + 200, 700, 36, .08)], layer="front", blur=34)


def metro(c: Canvas, pal: dict, m: dict) -> None:
    sky(c, pal)
    stars(c, m["stars"], (0, 0, W, 420), pal["star"], (0.5, 1.1), (0.1, 0.4))
    glow(c, 960, 790, 1300, 340, pal["city_glow"], 0.3)
    for x0, x1 in m["beams"]:
        fill = c.linear([(0, pal["beam"], 0), (0.7, pal["beam"], 0.05), (1, pal["beam"], 0.1)])
        c.back.append(f'<path d="M{x0 - 8},800 L{x0 + 8},800 L{x1 + 90},-40 L{x1 - 90},-40 Z" fill="{fill}" {c.blur(8)}/>')
    lim_x, lim_top = m["left_limit"]
    far, wins, beacons = [], [], []
    x = -20.0
    while x < W:
        w = c.rnd.uniform(56, 140)
        top = c.rnd.uniform(*m["far_tops"])
        if x < lim_x:
            top = max(top, lim_top + c.rnd.uniform(0, 140))
        step = c.rnd.uniform(10, 22)
        far.append(f'<rect x="{x:.1f}" y="{top:.1f}" width="{w:.1f}" height="{820 - top:.1f}"/>'
                   f'<rect x="{x + .12 * w:.1f}" y="{top - step:.1f}" width="{.76 * w:.1f}" height="{step + 1:.1f}"/>')
        if c.rnd.random() < 0.4:
            far.append(f'<rect x="{x + .32 * w:.1f}" y="{top - 2 * step:.1f}" width="{.36 * w:.1f}" height="{step + 1:.1f}"/>')
        for wy in range(int(top + 14), 800, 16):
            for wx in range(int(x + 8), int(x + w - 8), 12):
                if c.rnd.random() < 0.07:
                    wins.append(f'<rect x="{wx}" y="{wy}" width="3" height="5" fill-opacity="{c.rnd.uniform(.25, .55):.2f}"/>')
        if top < 470 and c.rnd.random() < 0.5:
            beacons.append((x + w / 2, top - 2 * step))
        x += w + c.rnd.uniform(-12, 10)
    tx, ttop, tw = m["tower"]
    far.append(f'<rect x="{tx - tw / 2}" y="{ttop + 120}" width="{tw}" height="{820 - ttop - 120}"/>'
               f'<rect x="{tx - tw * .38:.1f}" y="{ttop + 70}" width="{tw * .76:.1f}" height="52"/>'
               f'<rect x="{tx - tw * .24:.1f}" y="{ttop + 30}" width="{tw * .48:.1f}" height="42"/>'
               f'<path d="M{tx - 6},{ttop + 31} L{tx},{ttop - 40} L{tx + 6},{ttop + 31} Z"/>')
    strips = "".join(f'<rect x="{wx}" y="{wy}" width="4" height="6" fill-opacity="{c.rnd.uniform(.3, .65):.2f}"/>'
                     for wy in range(int(ttop + 134), 800, 15) for wx in range(int(tx - tw / 2 + 10), int(tx + tw / 2 - 10), 11)
                     if c.rnd.random() < 0.16)
    beacons.append((tx, ttop - 40))
    c.back.append(f'<g fill="{pal["far"]}">{"".join(far)}</g><g fill="{pal["window"]}">{"".join(wins)}{strips}</g>')
    c.back.append(f'<g fill="{pal["beacon"]}" {c.blur(4)}>' + "".join(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="7" fill-opacity=".5"/>' for bx, by in beacons)
                  + f'</g><g fill="{pal["beacon"]}">' + "".join(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="2.2"/>' for bx, by in beacons) + "</g>")
    glow(c, 960, 860, 1100, 160, pal["city_glow"], 0.16)
    near, lit = [], []
    x = -30.0
    while x < W + 30:
        w = c.rnd.uniform(90, 220)
        top = c.rnd.uniform(*m["near_tops"])
        near.append(f'<rect x="{x:.1f}" y="{top:.1f}" width="{w:.1f}" height="{H - top:.1f}"/>')
        if c.rnd.random() < 0.35:
            near.append(f'<rect x="{x + w * .6:.1f}" y="{top - 26:.1f}" width="22" height="27" rx="4"/>')
        for wy in range(int(top + 18), H, 20):
            for wx in range(int(x + 10), int(x + w - 12), 15):
                if c.rnd.random() < m["lit_windows"]:
                    color = pal["window_cool"] if c.rnd.random() < 0.15 else pal["window"]
                    lit.append(f'<rect x="{wx}" y="{wy}" width="6" height="9" fill="{color}" fill-opacity="{c.rnd.uniform(.35, .85):.2f}"/>')
        x += w + c.rnd.uniform(-6, 18)
    c.front.append(f'<g fill="{pal["near"]}">{"".join(near)}</g><g {c.blur(3)}>{"".join(lit)}</g><g>{"".join(lit)}</g>')


def sand(c: Canvas, pal: dict, m: dict) -> None:
    sky(c, pal)
    hz = m["horizon"]
    stars(c, m["stars"], (0, 0, W, hz - 250), pal["star"], (0.5, 1.4), (0.15, 0.7))
    glow(c, 980, hz, 1150, 250, pal["sun"], 0.3)
    mesas = []
    for x, w, h in [(140, 260, 40), (520, 180, 54), (1240, 300, 46), (1700, 220, 36)]:
        mesas.append(f'<path d="M{x - w / 2 - 30},{hz + 4} L{x - w / 2},{hz - h} L{x + w / 2},{hz - h} L{x + w / 2 + 34},{hz + 4} Z"/>')
    c.back.append(f'<g fill="{pal["mesa"]}">{"".join(mesas)}</g>')
    px, ptop, pw, ph = m["pyramid"]
    glow(c, px, ptop + ph * 0.8, pw * 0.95, ph * 0.9, pal["sun"], 0.18)
    # Seen from below: two faces meet at the near edge; the sunset under the horizon lights them from beneath.
    left = f"M{px - pw / 2},{ptop} L{px + pw * .04:.1f},{ptop} L{px},{ptop + ph} Z"
    right = f"M{px + pw * .04:.1f},{ptop} L{px + pw / 2},{ptop} L{px},{ptop + ph} Z"
    face = c.linear([(0, pal["pyramid"], 1), (0.45, pal["pyramid"], 1), (1, pal["pyramid_lit"], 1)], user=True, y1=ptop, y2=ptop + ph)
    edge = c.linear([(0, pal["pyramid_edge"], 0.1), (1, pal["pyramid_edge"], 0.7)], user=True, y1=ptop, y2=ptop + ph)
    n = m["tiers"]
    tiers = "".join(f'<path d="M{px - pw / 2 * (1 - i / n):.1f},{ptop + ph * i / n:.1f} L{px + pw / 2 * (1 - i / n):.1f},{ptop + ph * i / n:.1f}"/>'
                    for i in range(1, n))
    blocks = "".join(f'<rect x="{px - pw * .42 + i * pw * .1 + c.rnd.uniform(-6, 6):.1f}" y="{ptop - h:.1f}" width="{c.rnd.uniform(16, 30):.1f}" height="{h + 1:.1f}"/>'
                     for i, h in enumerate(c.rnd.uniform(8, 20) for _ in range(6)))
    c.back.append(f'<g fill="{pal["pyramid"]}">{blocks}</g><path d="{left}" fill="{face}"/><path d="{right}" fill="{face}"/>'
                  f'<path d="{right}" fill="#000" fill-opacity=".34"/>'
                  f'<g stroke="{pal["pyramid_edge"]}" stroke-opacity=".1" stroke-width="1.5">{tiers}</g>'
                  f'<path d="M{px + pw * .04:.1f},{ptop} L{px},{ptop + ph}" stroke="{pal["pyramid_edge"]}" stroke-opacity=".3" stroke-width="1.5"/>'
                  f'<path d="M{px - pw / 2},{ptop} L{px},{ptop + ph} L{px + pw / 2},{ptop}" fill="none" stroke="{edge}" stroke-width="2.2"/>'
                  f'<path d="M{px - pw / 2},{ptop} L{px + pw / 2},{ptop}" stroke="{pal["pyramid_edge"]}" stroke-opacity=".28" stroke-width="2"/>')
    debris = "".join(f'<rect x="{px + c.rnd.uniform(-70, 70):.1f}" y="{ptop + ph + c.rnd.uniform(14, 70):.1f}" width="{d:.1f}" height="{d:.1f}" '
                     f'transform="rotate({c.rnd.uniform(0, 90):.0f} {px:.0f} {ptop + ph + 40:.0f})"/>' for d in (c.rnd.uniform(3, 8) for _ in range(4)))
    c.back.append(f'<g fill="{pal["pyramid"]}">{debris}</g>'
                  f'<path d="M{px},{ptop + ph + 4} L{px},{hz}" stroke="{pal["pyramid_edge"]}" stroke-opacity=".12" stroke-width="5" {c.blur(3)}/>')
    for i, (base, amp) in enumerate(m["dunes"]):
        pts = wave(c.rnd, base, amp, c.rnd.uniform(1.1, 2.0))
        color = pal["dunes"][i]
        fill = c.linear([(0, color, 1), (1, pal["dunes"][-1], 1)], user=True, y1=base - amp, y2=H)
        crest = 0.38 - 0.07 * i
        layer = "front" if i == len(m["dunes"]) - 1 else "back"
        getattr(c, layer).append(f'<path d="{area(pts)}" fill="{fill}"/>'
                                 f'<path d="{polyline(pts)}" fill="none" stroke="{pal["crest"]}" stroke-opacity="{crest:.2f}" stroke-width="2.5" {c.blur(1.5)}/>')


def seaside(c: Canvas, pal: dict, m: dict) -> None:
    sky(c, pal)
    hz = m["horizon"]
    stars(c, m["stars"], (0, 0, W, hz - 240), pal["star"], (0.5, 1.3), (0.15, 0.6))
    mx, my, mr = m["moon"]
    glow(c, mx, my, mr * 6, mr * 6, pal["moon"], 0.16)
    c.back.append(f'<circle cx="{mx}" cy="{my}" r="{mr}" fill="{pal["moon"]}" fill-opacity=".82"/>')
    glow(c, 990, hz - 130, 460, 380, pal["glow"], 0.14)
    for side in (0, 1):
        pts = wave(c.rnd, hz + 2, 16, 3.2)
        pts = [(x, y if (x < 520 if side == 0 else x > 1460) else hz + 2) for x, y in pts]
        c.back.append(f'<path d="{area(pts, hz + 6)}" fill="{pal["island"]}"/>')
    shapes, edges, sparks, mirrored = [], [], [], []
    for x, top, w in m["spires"]:
        h = hz - top
        pts = [(x, top), (x + w / 2, top + h * .22), (x + w * .36, hz), (x - w * .36, hz), (x - w / 2, top + h * .22)]
        d = polyline(pts) + " Z"
        fill = c.linear([(0, pal["glass"], 0.42), (1, pal["glass"], 0.08)])
        shapes.append(f'<path d="{d}" fill="{fill}"/>')
        edges.append(f'<path d="{d}"/><path d="M{x},{top} L{x + w * .1:.1f},{hz} M{x - w / 2:.1f},{top + h * .22:.1f} L{x + w * .2:.1f},{top + h * .55:.1f}" stroke-opacity=".5"/>')
        sparks.append((x, top))
        mirrored.append(f'<path d="{polyline([(px, 2 * hz - py) for px, py in pts])} Z"/>')
    c.back.append(f'<g fill="{pal["glass"]}" fill-opacity=".07" {c.blur(3)}>{"".join(mirrored)}</g>')
    c.back.append(f'<g {c.blur(6)} opacity=".6">{"".join(shapes)}</g>' + "".join(shapes)
                  + f'<g fill="none" stroke="{pal["glass_edge"]}" stroke-opacity=".32" stroke-width="1.2">{"".join(edges)}</g>')
    c.back.append(f'<g fill="{pal["glass_edge"]}" {c.blur(4)}>' + "".join(f'<circle cx="{x}" cy="{y}" r="7" fill-opacity=".5"/>' for x, y in sparks)
                  + f'</g><g fill="{pal["glass_edge"]}">' + "".join(f'<circle cx="{x}" cy="{y}" r="1.8"/>' for x, y in sparks) + "</g>")
    streak = "".join(f'<ellipse cx="{mx + c.rnd.uniform(-10, 10):.1f}" cy="{hz + 10 + i * 15}" rx="{c.rnd.uniform(18, 60) * (1 - i / 26):.1f}" ry="2" '
                     f'fill-opacity="{.32 * (1 - i / 18):.2f}"/>' for i in range(18))
    ripples = "".join(f'<rect x="{c.rnd.uniform(-50, W):.0f}" y="{hz + 8 + (i / 40) ** 1.5 * 360:.1f}" width="{c.rnd.uniform(40, 220):.0f}" height="1.5" '
                      f'fill-opacity="{c.rnd.uniform(.05, .14):.2f}"/>' for i in range(60))
    c.back.append(f'<g fill="{pal["moon"]}" {c.blur(1.5)}>{streak}</g><g fill="{pal["glass_edge"]}">{ripples}</g>')
    back, front = [], []
    for _ in range(m["bubbles"]):
        r = 3 + 24 * c.rnd.random() ** 2.2
        x, y = c.rnd.uniform(880, 1900), c.rnd.uniform(170, 960)
        bubble = (f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" stroke-width="{1 + r / 18:.2f}" stroke-opacity="{c.rnd.uniform(.3, .6):.2f}"/>'
                  f'<circle cx="{x - r * .35:.1f}" cy="{y - r * .35:.1f}" r="{max(.8, r * .18):.1f}" fill="{pal["bubble"]}" fill-opacity=".55" stroke="none"/>')
        (front if r < 9 and c.rnd.random() < 0.35 else back).append(bubble)
    c.back.append(f'<g fill="none" stroke="{pal["bubble"]}">{"".join(back)}</g>')
    c.front.append(f'<g fill="none" stroke="{pal["bubble"]}">{"".join(front)}</g>')


def trunk(c: Canvas, x, w, flare, color) -> str:
    """A tall trunk running off the top of the frame, flaring into roots at y = flare; a few broken branches."""
    lean = c.rnd.uniform(-14, 14)
    body = (f'<path d="M{x - w * .42 + lean:.1f},-10 L{x + w * .42 + lean:.1f},-10 Q{x + w * .5:.1f},{flare * .6:.1f} {x + w * .5:.1f},{flare - 70:.1f} '
            f'Q{x + w * .6:.1f},{flare - 10:.1f} {x + w * 1.4:.1f},{flare + 24:.1f} L{x + w * 1.4:.1f},{H} L{x - w * 1.4:.1f},{H} '
            f'L{x - w * 1.4:.1f},{flare + 24:.1f} Q{x - w * .6:.1f},{flare - 10:.1f} {x - w * .5:.1f},{flare - 70:.1f} '
            f'Q{x - w * .5:.1f},{flare * .6:.1f} {x - w * .42 + lean:.1f},-10 Z"/>')
    for _ in range(c.rnd.choice([1, 2, 2, 3])):
        side = c.rnd.choice([-1, 1])
        y, length = c.rnd.uniform(60, flare - 160), c.rnd.uniform(.8, 1.8) * w
        body += (f'<path d="M{x + side * w * .4:.1f},{y:.1f} L{x + side * (w * .4 + length):.1f},{y - length * .55:.1f} '
                 f'L{x + side * (w * .4 + length):.1f},{y - length * .55 + 8:.1f} L{x + side * w * .4:.1f},{y + w * .22:.1f} Z"/>')
    return f'<g fill="{color}">{body}</g>'


def wooded(c: Canvas, pal: dict, m: dict) -> None:
    sky(c, pal)
    glow(c, 1000, 540, 1350, 260, pal["haze"], 0.2)
    for x0, x1, w in [(1560, 820, 110), (1320, 520, 70), (1800, 1180, 80)]:
        fill = c.linear([(0, pal["shaft"], 0.07), (1, pal["shaft"], 0)])
        c.back.append(f'<path d="M{x0 - w},-20 L{x0 + w},-20 L{x1 + w * 1.8:.0f},960 L{x1 - w * 1.8:.0f},960 Z" fill="{fill}" {c.blur(24)}/>')
    c.back.append("".join(trunk(c, x, w, c.rnd.uniform(560, 610), pal["far"]) for x, w in m["far_trunks"]))
    # Ground fog hides where the far trunks meet the ground.
    mist(c, pal["haze"], [(x, c.rnd.uniform(560, 600), c.rnd.uniform(260, 420), c.rnd.uniform(34, 50), c.rnd.uniform(.14, .2))
                          for x in range(0, W + 200, 220)], blur=30)
    x0, x1, by = m["bridge"]
    metal = pal["metal"]
    ribs = "".join(f'<rect x="{x:.0f}" y="{by - 30}" width="3" height="32"/>' for x in range(int(x0), int(x1), 44))
    braces = "".join(f'<path d="M{x},{by} L{x + 44},{by - 30}" />' for x in range(int(x0), int(x1) - 40, 88))
    lamps = [(x, by + 34) for x in range(int(x0) + 70, int(x1), 150)]
    c.back.append(f'<g fill="{metal[2]}"><rect x="{x0}" y="{by - 34}" width="{x1 - x0}" height="6"/><rect x="{x0}" y="{by}" width="{x1 - x0}" height="9"/>{ribs}</g>'
                  f'<g fill="none" stroke="{metal[2]}" stroke-width="3">{braces}</g>'
                  f'<g stroke="{metal[2]}" stroke-width="2">' + "".join(f'<path d="M{x},{by + 9} L{x},{y - 5}"/>' for x, y in lamps) + "</g>"
                  f'<g fill="{pal["lamp"]}" {c.blur(7)}>' + "".join(f'<circle cx="{x}" cy="{y}" r="14" fill-opacity=".45"/>' for x, y in lamps) + "</g>"
                  f'<g fill="{pal["lamp"]}">' + "".join(f'<circle cx="{x}" cy="{y}" r="4"/>' for x, y in lamps) + "</g>")
    vines_halo, vines = [], []
    for x, w in m["mid_trunks"]:
        c.back.append(trunk(c, x, w, c.rnd.uniform(600, 640), pal["mid"]))
        for _ in range(5 if x > 760 else 0):
            fx, fy = x + c.rnd.choice([-1, 1]) * w * c.rnd.uniform(.3, .5), c.rnd.uniform(240, 520)
            color = c.rnd.choice(pal["flowers"])
            vines_halo.append(f'<circle cx="{fx:.1f}" cy="{fy:.1f}" r="{c.rnd.uniform(8, 13):.1f}" fill="{color}" fill-opacity=".4"/>')
            vines.append(f'<circle cx="{fx:.1f}" cy="{fy:.1f}" r="{c.rnd.uniform(2.2, 3.6):.1f}" fill="{color}"/>')
    c.back.append(f'<g {c.blur(5)}>{"".join(vines_halo)}</g>{"".join(vines)}')
    px, spout = m["pipe"]
    side = c.linear([(0, metal[0], 1), (.45, metal[2], 1), (1, metal[1], 1)], x2=1, y2=0)
    flat = c.linear([(0, metal[0], 1), (.45, metal[2], 1), (1, metal[1], 1)])
    flanges = "".join(f'<rect x="{px - 18}" y="{y}" width="36" height="12" rx="3"/>' for y in range(40, 600, 120))
    c.back.append(f'<rect x="{px - 13}" y="-10" width="26" height="620" fill="{side}"/>'
                  f'<rect x="{px - 70}" y="{spout - 11}" width="60" height="22" fill="{flat}"/>'
                  f'<rect x="{px - 82}" y="{spout - 16}" width="14" height="32" rx="3" fill="{pal["flange"]}"/>'
                  f'<g fill="{pal["flange"]}">{flanges}</g>'
                  f'<circle cx="{px}" cy="{spout + 80}" r="22" fill="none" stroke="{pal["flange"]}" stroke-width="5"/>'
                  f'<path d="M{px - 22},{spout + 80} L{px + 22},{spout + 80} M{px},{spout + 58} L{px},{spout + 102}" stroke="{pal["flange"]}" stroke-width="3"/>'
                  f'<rect x="-10" y="596" width="{W + 20}" height="28" fill="{flat}"/>'
                  f'<g fill="{pal["flange"]}">' + "".join(f'<rect x="{x}" y="590" width="16" height="40" rx="3"/>' for x in range(120, W, 260)) + "</g>")
    # Steam leaves the spout sideways, then rises and curls as it spreads.
    puffs = [(px - 100 - i * 8 + 26 * math.sin(i * 0.9), spout - 4 - i * 30, 16 + i * 10, 14 + i * 9, max(0.015, 0.15 - i * 0.013)) for i in range(10)]
    mist(c, pal["steam"], puffs, blur=12)
    clusters = []
    for x in range(-20, 820, 36):
        for _ in range(3):
            clusters.append(f'<path d="{leaf(x + c.rnd.uniform(-20, 20), c.rnd.uniform(-30, 10), c.rnd.uniform(40, 140), c.rnd.uniform(70, 180) * (1 - x / 1400), c.rnd.uniform(14, 26), c.rnd.uniform(-10, 30))}"/>')
    for x in range(1480, W + 40, 44):
        for _ in range(2):
            clusters.append(f'<path d="{leaf(x + c.rnd.uniform(-20, 20), c.rnd.uniform(-30, 0), c.rnd.uniform(60, 130), c.rnd.uniform(50, 110), c.rnd.uniform(12, 20), c.rnd.uniform(-10, 20))}"/>')
    c.back.append(f'<g fill="{pal["canopy"]}">{"".join(clusters)}</g>')
    lx, ly = m["ledge"]
    ledge = f"M{lx - 390},{H} L{lx - 360},{ly + 50} Q{lx - 300},{ly - 4} {lx - 120},{ly} Q{lx + 80},{ly - 10} {lx + 260},{ly + 6} Q{lx + 370},{ly + 30} {lx + 400},{H} Z"
    moss = f"M{lx - 352},{ly + 46} Q{lx - 300},{ly - 4} {lx - 120},{ly} Q{lx + 80},{ly - 10} {lx + 260},{ly + 6} Q{lx + 360},{ly + 28} {lx + 384},{ly + 80}"
    c.back.append(f'<path d="{ledge}" fill="{pal["ledge"]}"/><path d="{moss}" fill="none" stroke="{pal["moss"]}" stroke-width="8" stroke-linecap="round"/>')
    c.front.append(f'<g fill="{pal["near"]}"><path d="M-30,-10 L150,-10 Q120,520 190,{H} L-30,{H} Z"/>'
                   f'<path d="M{W - 60},-10 L{W + 20},-10 L{W + 20},{H} L{W - 120},{H} Q{W - 52},520 {W - 60},-10 Z"/></g>')
    halos, dots = [], []
    for i in range(m["flowers"]):
        if i < 12:
            x, y = c.rnd.uniform(lx - 330, lx + 360), ly + c.rnd.uniform(-4, 22)
        else:
            x, y = c.rnd.uniform(200, 1150), c.rnd.uniform(650, 900)
        color = c.rnd.choice(pal["flowers"])
        r = c.rnd.uniform(2.5, 5.5)
        halos.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r * 3:.1f}" fill="{color}" fill-opacity=".45"/>')
        dots.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" fill="{color}"/>')
    c.front.append(f'<g {c.blur(6)}>{"".join(halos)}</g>{"".join(dots)}')
    spores = "".join(f'<circle cx="{c.rnd.uniform(700, 1900):.0f}" cy="{c.rnd.uniform(220, 620):.0f}" r="{c.rnd.uniform(1, 2.4):.1f}" '
                     f'fill-opacity="{c.rnd.uniform(.25, .7):.2f}"/>' for _ in range(m["spores"]))
    c.front.append(f'<g fill="{pal["flowers"][2]}" {c.blur(1.2)}>{spores}</g>')


def palm(c: Canvas, base_x, base_y, top_x, top_y, width, fronds, color, layer="back") -> None:
    bend = (base_x + top_x) / 2 + (top_y - base_y) * 0.08
    trunk = (f"M{base_x - width:.1f},{base_y:.1f} Q{bend - width * .6:.1f},{(base_y + top_y) / 2:.1f} {top_x - width * .35:.1f},{top_y:.1f} "
             f"L{top_x + width * .35:.1f},{top_y:.1f} Q{bend + width * .6:.1f},{(base_y + top_y) / 2:.1f} {base_x + width:.1f},{base_y:.1f} Z")
    leaves = "".join(f'<path d="{leaf(top_x, top_y, angle, length, length * .16, length * droop)}"/>' for angle, length, droop in fronds)
    getattr(c, layer).append(f'<g fill="{color}"><path d="{trunk}"/>{leaves}</g>')


def lost(c: Canvas, pal: dict, m: dict) -> None:
    sky(c, pal)
    hz = m["horizon"]
    stars(c, m["stars"], (0, 0, W, hz - 260), pal["star"], (0.5, 1.3), (0.12, 0.6))
    mx, my, mr = m["moon"]
    glow(c, mx, my, mr * 5, mr * 5, pal["moon"], 0.18)
    c.back.append(f'<circle cx="{mx}" cy="{my}" r="{mr}" fill="{pal["moon"]}" fill-opacity=".55"/>')
    mist(c, pal["cloud"], [(mx - 120, my + 30, 300, 20, .85), (mx + 160, my - 20, 240, 14, .7), (mx + 40, my + 80, 420, 24, .8),
                           (380, 300, 500, 30, .6), (1500, 160, 520, 26, .5)], blur=12)
    glow(c, 960, hz + 40, 1200, 160, pal["swamp"], 0.14)
    ripples = "".join(f'<ellipse cx="{c.rnd.uniform(0, W):.0f}" cy="{hz + 20 + (i / 30) ** 1.4 * 300:.1f}" rx="{c.rnd.uniform(40, 200):.0f}" '
                      f'ry="{c.rnd.uniform(3, 8):.1f}" stroke-opacity="{c.rnd.uniform(.08, .22):.2f}"/>' for i in range(30))
    c.back.append(f'<g fill="none" stroke="{pal["swamp"]}" stroke-width="1.5">{ripples}</g>')
    island = f"M380,{hz + 4} Q560,{hz - 50} 780,{hz - 60} Q960,{hz - 92} 1160,{hz - 52} Q1300,{hz - 24} 1380,{hz + 4} Z"
    c.back.append(f'<path d="{island}" fill="{pal["island"]}"/>')
    for x, y, h in [(820, hz - 66, 130), (930, hz - 84, 180), (1010, hz - 80, 120), (1180, hz - 46, 140)]:
        lean = c.rnd.uniform(-30, 30)
        palm(c, x, y + 6, x + lean, y - h, 5, [(a, h * .45, c.rnd.uniform(.05, .2)) for a in (-170, -140, -100, -60, -20, 10)], pal["island"])
    palm(c, -20, H, 150, 120, 30, [(-200, 380, .25), (-160, 360, .3), (-120, 300, .22), (-60, 360, .28), (-20, 420, .32), (20, 380, .4),
                                   (60, 300, .5), (-90, 260, .1)], pal["palm"])
    big = "".join(f'<path d="{leaf(x, y, a, l, l * .32, l * d)}"/>' for x, y, a, l, d in
                  [(1960, 1100, -150, 520, -.1), (1960, 1100, -125, 460, -.05), (1940, 1120, -170, 600, .05),
                   (-40, 1100, -30, 560, -.05), (-40, 1100, -55, 420, -.1), (-30, 1120, -8, 640, .08)])
    c.front.append(f'<g fill="{pal["palm"]}">{big}</g>')
    halos, dots = [], []
    for _ in range(26):
        x = c.rnd.choice([c.rnd.uniform(40, 560), c.rnd.uniform(1260, 1900)])
        y = c.rnd.uniform(830, 1020) if x > 1000 else c.rnd.uniform(790, 1000)
        color = c.rnd.choice(pal["flowers"])
        halos.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{c.rnd.uniform(8, 16):.1f}" fill="{color}" fill-opacity=".45"/>')
        dots.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{c.rnd.uniform(2.5, 4.5):.1f}" fill="{color}"/>')
    c.front.append(f'<g {c.blur(6)}>{"".join(halos)}</g>{"".join(dots)}')
    flies = "".join(f'<circle cx="{c.rnd.uniform(300, 1900):.0f}" cy="{c.rnd.uniform(300, 900):.0f}" r="{c.rnd.uniform(1.2, 2.6):.1f}" '
                    f'fill-opacity="{c.rnd.uniform(.35, .9):.2f}"/>' for _ in range(m["fireflies"]))
    c.front.append(f'<g fill="{pal["firefly"]}" {c.blur(3)}>{flies}</g><g fill="{pal["firefly"]}">{flies}</g>')


def fork(x, top, tilt, scale, color, rim) -> str:
    """A giant fork stuck in the ground: four tines, a rounded head and a long handle."""
    s = scale
    tine, gap, tl = 14 * s, 11 * s, 150 * s
    head = 4 * tine + 3 * gap
    parts = [f'<rect x="{-head / 2 + i * (tine + gap):.1f}" y="0" width="{tine:.1f}" height="{tl + 10 * s:.1f}" rx="{tine / 2:.1f}"/>' for i in range(4)]
    parts.append(f'<path d="M{-head / 2:.1f},{tl:.1f} L{head / 2:.1f},{tl:.1f} Q{head / 2:.1f},{tl + 60 * s:.1f} {16 * s:.1f},{tl + 90 * s:.1f} '
                 f'L{15 * s:.1f},{1200:.1f} L{-15 * s:.1f},{1200:.1f} L{-16 * s:.1f},{tl + 90 * s:.1f} Q{-head / 2:.1f},{tl + 60 * s:.1f} {-head / 2:.1f},{tl:.1f} Z"/>')
    shine = f'<path d="M{-head / 2 + 2:.1f},{tl + 6:.1f} Q{-head / 2:.1f},{tl + 60 * s:.1f} {-14 * s:.1f},{tl + 92 * s:.1f} L{-13 * s:.1f},{700:.1f}" fill="none" stroke="{rim}" stroke-width="2" stroke-opacity=".5"/>'
    return f'<g transform="translate({x} {top}) rotate({tilt})" fill="{color}">{"".join(parts)}{shine}</g>'


def luncheon(c: Canvas, pal: dict, m: dict) -> None:
    sky(c, pal)
    stars(c, m["stars"], (700, 0, W, 300), pal["star"], (0.5, 1.2), (0.1, 0.45))
    px, py = m["peak"]
    foot = 700
    # The lava light comes from behind Mount Volbono, so the cone reads as a dark silhouette.
    glow(c, px, 560, 1100, 300, pal["lava"], 0.24)
    mist(c, pal["smoke"], [(220, 60, 520, 120, .9), (700, 30, 480, 90, .7), (1500, 20, 520, 80, .6), (px, 150, 360, 50, .45)], blur=30)
    glow(c, px, py - 50, 300, 190, pal["lava"], 0.28)
    mist(c, pal["lava_core"], [(px + 10 + i * 14, py - 46 - i * 44, 50 + i * 20, 18 + i * 9, max(0.02, 0.11 - i * 0.018)) for i in range(6)], blur=18)
    rim = 108
    mountain = (f"M{px - 760},{foot} C{px - 520},{foot - 60} {px - 300},{py + 170} {px - rim - 16},{py + 26} "
                f"L{px + rim + 16},{py + 26} C{px + 300},{py + 170} {px + 540},{foot - 60} {px + 780},{foot} Z")
    c.back.append(f'<path d="{mountain}" fill="{c.linear([(0, pal["mountain_lit"], 1), (0.45, pal["mountain"], 1)])}"/>'
                  f'<path d="{mountain}" fill="none" stroke="{pal["lava"]}" stroke-opacity=".28" stroke-width="2.5" {c.blur(2)}/>')
    # Thin lava runs: they start below the rim, wander down the slope and fade out as they cool.
    runs = []
    for start, end, top, width in [(-150, -330, 70, 5), (-60, -150, 120, 3.5), (150, 300, 80, 4.5), (70, 120, 150, 3)]:
        phase = c.rnd.uniform(0, math.tau)
        pts = []
        for i in range(11):
            t = i / 10
            pts.append((px + start + (end - start) * t + math.sin(t * 2.6 * math.pi + phase) * 14, py + top + (foot - py - top - 40) * t))
        fade = c.linear([(0, pal["lava"], 1), (0.75, pal["lava"], 0.5), (1, pal["lava"], 0)], user=True, y1=py + top, y2=foot - 40)
        runs.append(f'<path d="{smooth(pts)}" stroke="{fade}" stroke-width="{width}"/>')
    c.back.append(f'<g fill="none" stroke-linecap="round" {c.blur(5)} opacity=".7">{"".join(runs)}</g>'
                  f'<g fill="none" stroke-linecap="round" opacity=".85">{"".join(runs)}</g>')
    pot = (f"M{px - rim - 20},{py - 6} L{px + rim + 20},{py - 6} Q{px + rim + 16},{py + 52} {px + rim - 26},{py + 74} "
           f"L{px - rim + 26},{py + 74} Q{px - rim - 16},{py + 52} {px - rim - 20},{py - 6} Z")
    c.back.append(f'<path d="{pot}" fill="{pal["pot"]}"/>'
                  f'<path d="M{px - rim - 34},{py + 10} q-22,-4 -24,20 q2,20 22,16 M{px + rim + 34},{py + 10} q22,-4 24,20 q-2,20 -22,16" fill="none" stroke="{pal["pot"]}" stroke-width="10"/>'
                  f'<ellipse cx="{px}" cy="{py - 6}" rx="{rim + 20}" ry="14" fill="{pal["lava"]}" fill-opacity=".85"/>'
                  f'<ellipse cx="{px}" cy="{py - 8}" rx="{rim - 10}" ry="7" fill="{pal["lava_core"]}" fill-opacity=".8" {c.blur(3)}/>'
                  f'<ellipse cx="{px}" cy="{py - 6}" rx="{rim + 24}" ry="16" fill="none" stroke="{pal["pot"]}" stroke-width="6"/>')
    ground = wave(c.rnd, foot - 30, 14, 1.4)
    cracks = "".join(f'<path d="{polyline([(x + i * 26 + c.rnd.uniform(-6, 6), at(ground, x) + 14 + i * 16 + c.rnd.uniform(-4, 4)) for i in range(5)])}"/>'
                     for x in (c.rnd.uniform(100, 1800) for _ in range(9)))
    c.back.append(f'<path d="{area(ground)}" fill="{pal["pot"]}"/>'
                  f'<g fill="none" stroke="{pal["lava"]}" stroke-width="2.5" stroke-opacity=".28" {c.blur(2)}>{cracks}</g>')
    ox, oy, orx, ory = m["pool"]
    glow(c, ox, oy, orx * 1.6, ory * 3.2, pal["lava"], 0.34)
    c.back.append(f'<ellipse cx="{ox}" cy="{oy}" rx="{orx}" ry="{ory}" fill="{pal["lava"]}" fill-opacity=".5" {c.blur(6)}/>'
                  f'<ellipse cx="{ox}" cy="{oy - 3}" rx="{orx * .7:.0f}" ry="{ory * .4:.0f}" fill="{pal["lava_core"]}" fill-opacity=".45" {c.blur(5)}/>')
    for x, top, tilt, scale in m["forks"]:
        layer = "front" if x > 1700 else "back"
        getattr(c, layer).append(fork(x, top, tilt, scale, pal["fork"], pal["fork_rim"]))
    embers = "".join(f'<circle cx="{c.rnd.uniform(700, 1900):.0f}" cy="{c.rnd.uniform(180, 700):.0f}" r="{c.rnd.uniform(1, 2.4):.1f}" '
                     f'fill-opacity="{c.rnd.uniform(.3, .85):.2f}"/>' for _ in range(m["embers"]))
    c.front.append(f'<g fill="{pal["ember"]}" {c.blur(2.5)}>{embers}</g><g fill="{pal["ember"]}">{embers}</g>')


def moon(c: Canvas, pal: dict, m: dict) -> None:
    sky(c, pal)
    hz = m["horizon"]
    c.back.append(f'<ellipse cx="1350" cy="260" rx="900" ry="120" transform="rotate(24 1350 260)" fill="{pal["milky"]}" fill-opacity=".08" {c.blur(40)}/>')
    stars(c, m["stars"], (0, 0, W, hz - 20), pal["star"], (0.4, 1.3), (0.15, 0.85), fade=0.4)
    px, py, pr = m["planet"]
    glow(c, px, py, pr * 2.1, pr * 2.1, pal["atmosphere"], 0.2)
    clip = c.new_id("clip")
    c.defs.append(f'<clipPath id="{clip}"><circle cx="{px}" cy="{py}" r="{pr}"/></clipPath>')
    base = c.radial([(0, pal["planet_lit"], 1), (1, pal["planet"], 1)], cx=0.68, cy=0.32, r=0.75)
    lands = "".join(f'<circle cx="{px + c.rnd.uniform(-pr, pr):.0f}" cy="{py + c.rnd.uniform(-pr, pr):.0f}" r="{c.rnd.uniform(14, 46):.0f}"/>'
                    for _ in range(26))
    clouds = "".join(f'<ellipse cx="{px + c.rnd.uniform(-pr, pr):.0f}" cy="{py + c.rnd.uniform(-pr, pr):.0f}" rx="{c.rnd.uniform(30, 90):.0f}" ry="{c.rnd.uniform(4, 9):.0f}" '
                     f'transform="rotate({c.rnd.uniform(-20, 20):.0f} {px} {py})"/>' for _ in range(12))
    c.back.append(f'<g clip-path="url(#{clip})"><circle cx="{px}" cy="{py}" r="{pr}" fill="{base}"/>'
                  f'<g fill="{pal["land"]}" fill-opacity=".55" {c.blur(3)}>{lands}</g>'
                  f'<g fill="{pal["cloud"]}" fill-opacity=".2" {c.blur(4)}>{clouds}</g>'
                  f'<circle cx="{px - pr * .62:.0f}" cy="{py + pr * .4:.0f}" r="{pr * 1.08:.0f}" fill="#000" fill-opacity=".72" {c.blur(22)}/></g>'
                  f'<circle cx="{px}" cy="{py}" r="{pr}" fill="none" stroke="{pal["atmosphere"]}" stroke-opacity=".4" stroke-width="3" {c.blur(2)}/>')
    far = wave(c.rnd, hz - 26, 22, 1.6)
    c.back.append(f'<path d="{area(far)}" fill="{pal["ground"][0]}" fill-opacity=".55"/>')
    cx, cb = m["chapel"]
    k = 0.72
    c.back.append(f'<g fill="{pal["chapel"]}" transform="translate({cx} {cb}) scale({k})"><rect x="-40" y="-50" width="80" height="52"/>'
                  f'<path d="M-48,-48 L0,-92 L48,-48 Z"/><rect x="-12" y="-140" width="24" height="60"/><path d="M-15,-138 L0,-190 L15,-138 Z"/></g>'
                  f'<circle cx="{cx}" cy="{cb - 64 * k:.1f}" r="8" fill="{pal["window"]}" fill-opacity=".5" {c.blur(5)}/>'
                  f'<circle cx="{cx}" cy="{cb - 64 * k:.1f}" r="3.4" fill="{pal["window"]}" fill-opacity=".8"/>'
                  f'<rect x="{cx - 5}" y="{cb - 18}" width="10" height="18" rx="5" fill="{pal["window"]}" fill-opacity=".35"/>')
    ground = wave(c.rnd, hz, 18, 1.1)
    fill = c.linear([(0, pal["ground"][0], 1), (1, pal["ground"][1], 1)], user=True, y1=hz - 20, y2=H)
    c.back.append(f'<path d="{area(ground)}" fill="{fill}"/>'
                  f'<path d="{polyline(ground)}" fill="none" stroke="{pal["crater_rim"]}" stroke-opacity=".45" stroke-width="2" {c.blur(1)}/>')
    craters = []
    for _ in range(m["craters"]):
        x = c.rnd.uniform(0, W)
        y = c.rnd.uniform(at(ground, x) + 24, H - 20)
        rx = c.rnd.uniform(26, 120) * (0.5 + (y - hz) / (H - hz))
        ry = rx * 0.24
        craters.append(f'<ellipse cx="{x:.0f}" cy="{y:.0f}" rx="{rx:.0f}" ry="{ry:.1f}" fill="{pal["crater"]}"/>'
                       f'<path d="M{x - rx:.0f},{y:.0f} A{rx:.0f},{ry:.1f} 0 0 0 {x + rx:.0f},{y:.0f}" fill="none" stroke="{pal["crater_rim"]}" stroke-opacity=".6" stroke-width="2"/>')
    c.back.append("".join(craters))


MOTIFS = {"cap": cap, "metro": metro, "sand": sand, "seaside": seaside, "wooded": wooded, "lost": lost,
          "luncheon": luncheon, "moon": moon}


# ---- page -------------------------------------------------------------------------------------

def place(c: Canvas, ch: dict, tint_color: str) -> None:
    """The character between the scene's back and front layers, toned to the scene's light."""
    left, top, width, height = ch["box"]
    cx = left + width / 2
    if ch["backlight"]:
        glow(c, cx, top + height * 0.45, width * 0.85, height * 0.62, ch["glow"], ch["backlight"])
    if ch["shadow"]:
        c.back.append(f'<ellipse cx="{cx:.1f}" cy="{top + height - 10:.1f}" rx="{width * .38:.1f}" ry="20" fill="#000" fill-opacity=".5" {c.blur(10)}/>')
    flip = f' transform="translate({2 * cx:.1f} 0) scale(-1 1)"' if ch["flip"] else ""
    image = (f'<image href="{html.escape(ch["src"])}" x="{left:.1f}" y="{top}" width="{width:.1f}" height="{height}" '
             f'preserveAspectRatio="none"{flip}')
    alpha = c.new_id("alpha")
    c.defs.append(f'<mask id="{alpha}" mask-type="alpha">{image}/></mask>')
    tint = c.linear([(0, tint_color, 0), (0.4, tint_color, 0), (1, tint_color, ch["tint"])], user=True, y1=top, y2=top + height)
    glow_css = f"drop-shadow(0 0 26px {rgb(ch['glow'], ch['glow_alpha'])})" if ch["glow_alpha"] else ""
    body = (f'{image} style="filter:brightness({ch["brightness"]:g}) saturate({ch["saturate"]:g}) {glow_css}">'
            f'<title>{html.escape(ch["name"]["zh"])} · {html.escape(ch["name"]["en"])}</title></image>'
            f'<rect x="{left:.1f}" y="{top}" width="{width:.1f}" height="{height}" fill="{tint}" mask="url(#{alpha})"/>')
    if ch["fade"]:
        fade = c.new_id("fade")
        ramp = c.linear([(0, "#FFFFFF", 1), (1 - ch["fade"], "#FFFFFF", 1), (1, "#FFFFFF", 0)], user=True, y1=top, y2=top + height)
        c.defs.append(f'<mask id="{fade}"><rect x="{left - 60:.1f}" y="{top - 60}" width="{width + 120:.1f}" height="{height + 60}" fill="{ramp}"/></mask>')
        body = f'<g mask="url(#{fade})">{body}</g>'
    c.cast.append(body)


def shade_css(shade: dict) -> str:
    """The baked legibility shade: dark bottom band, a soft pool behind the clock, a light top edge."""
    color = shade["color"]
    bottom = ", ".join(f"{rgb(color, a)} {o * 100:g}%" for o, a in shade["bottom"])
    k, t = shade["clock"], shade["top"]
    return (f"radial-gradient({k['rx']}px {k['ry']}px at {k['cx']}px {k['cy']}px, {rgb(color, k['alpha'])}, "
            f"{rgb(color, k['alpha'] * .55)} 45%, {rgb(color, 0)} 100%), "
            f"linear-gradient({bottom}), "
            f"linear-gradient({rgb(color, t['alpha'])}, {rgb(color, 0)} {t['height'] * 100:g}%)")


def guides_html(guides: list) -> str:
    return "".join(f'<div class="guide" style="left:{x}px;top:{y}px;width:{w}px;height:{h}px"><span>{html.escape(label)}</span></div>'
                   for label, (x, y, w, h) in ((g["label"], g["box"]) for g in guides))


def faces_html(characters: list) -> str:
    """A line under each character's face, to check it against the dock guides."""
    return "".join(f'<div class="face" style="left:{ch["box"][0] - 16:.1f}px;top:{ch["face_y"]}px;width:{ch["box"][2] + 32:.1f}px">'
                   f'<span>脸部下沿 y {ch["face_y"]:.0f}</span></div>' for ch in characters)


def script_json(value: object) -> str:
    """Keep embedded JSON safe inside a script element without changing data."""
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":"))
            .replace("</", "<\\/")
            .replace(" ", "\\u2028")
            .replace(" ", "\\u2029"))


HTML = r'''<!doctype html>
<html lang="zh-CN"><head>
<meta charset="utf-8"><meta name="viewport" content="width=1920">
<title>超级马力欧 奥德赛 · 电视壁纸</title>
<style>
*{box-sizing:border-box}html,body{margin:0;background:#05070D}body{width:1920px;color:#E8ECF5;font:15px/1.4 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif}.wallpaper{position:relative;width:1920px;height:1080px;overflow:hidden;isolation:isolate;margin:0 0 24px;filter:url(#grain);background:__SHADE_COLOR__}.wallpaper:last-of-type{margin-bottom:0}.scene{position:absolute;inset:0;display:block}.shade{position:absolute;inset:0;background:__SHADE__}.guides{display:none;position:absolute;inset:0}html.show-guides .guides{display:block}.guide{position:absolute;border:2px dashed #FFFFFFB0;border-radius:14px;background:#FFFFFF14}.guide span{position:absolute;left:10px;top:6px;font-size:16px;color:#FFFFFFD0}.face{position:absolute;border-top:2px solid #FFD54AE6}.face span{position:absolute;right:0;bottom:4px;font-size:14px;color:#FFD54A}
</style></head><body>
<svg width="0" height="0" style="position:absolute" aria-hidden="true"><filter id="grain" x="0" y="0" width="100%" height="100%" color-interpolation-filters="sRGB"><feTurbulence type="fractalNoise" baseFrequency="__GRAIN_FREQUENCY__" numOctaves="__GRAIN_OCTAVES__" seed="__GRAIN_SEED__" result="noise"/><feColorMatrix in="noise" type="matrix" values="1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 0 0 0 0 1" result="mono"/><feComposite in="SourceGraphic" in2="mono" operator="arithmetic" k1="0" k2="1" k3="__GRAIN_K3__" k4="__GRAIN_K4__"/></filter></svg>
__SECTIONS__
<script>
window.__wallpapers=__WALLPAPERS__;
if(location.hash==='#guides')document.documentElement.classList.add('show-guides');
</script></body></html>'''


def build(config: dict) -> tuple[str, list[dict]]:
    sections, records = [], []
    shade = shade_css(config["legibility"])
    guides = guides_html(config["guides"])
    for n, scene in enumerate(config["scenes"]):
        c = Canvas(f"w{n + 1}", scene["motif"]["seed"])
        MOTIFS[scene["motif"]["type"]](c, scene["palette"], scene["motif"])
        for ch in scene["characters"]:
            place(c, ch, scene["palette"].get("tint", config["legibility"]["color"]))
        label = f'{scene["title"]["zh"]} · {scene["title"]["en"]}'
        sections.append(f'<section class="wallpaper" id="{scene["id"]}" data-file="{scene["id"]}.jpg" aria-label="{html.escape(label)}">'
                        f'{c.svg()}<div class="shade"></div><div class="guides" aria-hidden="true">{guides}{faces_html(scene["characters"])}</div></section>')
        records.append({"id": scene["id"], "file": scene["id"] + ".jpg", "title": scene["title"], "mood": scene["mood"],
                        "characters": [{"id": ch["id"], "name": ch["name"], "box": ch["box"], "face_y": ch["face_y"], "native": ch["native"],
                                        "scale": round(ch["box"][3] / ch["native"][1], 4)} for ch in scene["characters"]]})
    grain = config["grain"]
    page = (HTML.replace("__SHADE_COLOR__", config["legibility"]["color"])
            .replace("__SHADE__", shade)
            .replace("__GRAIN_FREQUENCY__", f'{grain["frequency"]:g}')
            .replace("__GRAIN_OCTAVES__", f'{int(grain["octaves"])}')
            .replace("__GRAIN_SEED__", f'{int(grain["seed"])}')
            .replace("__GRAIN_K3__", f'{grain["strength"]:g}')
            .replace("__GRAIN_K4__", f'{-grain["strength"] / 2:g}')
            .replace("__SECTIONS__", "\n".join(sections)))
    meta = {"output": config["output"], "jpeg_quality": config["jpeg_quality"], "grain": grain, "wallpapers": records}
    return page.replace("__WALLPAPERS__", script_json(meta)), records


def main() -> None:
    if len(sys.argv) > 2:
        raise SystemExit("Usage: python3 -I make_wallpapers.py [scenes.json]")
    path = Path(sys.argv[1]).expanduser().resolve() if len(sys.argv) > 1 else DEFAULT_SCENES
    page, records = build(load_scenes(path))
    output = HERE / "wallpapers.html"
    output.write_text(page, encoding="utf-8")
    print(f"Built {output} for {len(records)} wallpapers.")
    for record in records:
        for ch in record["characters"]:
            left, top, width, height = ch["box"]
            print(f"  {record['id']:<12} {ch['id']:<10} {width:.0f}x{height} at ({left:.0f}, {top}), face to y {ch['face_y']:.0f}"
                  f" = {ch['scale']:.3f} of native")


if __name__ == "__main__":
    main()
