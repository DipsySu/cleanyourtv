#!/usr/bin/env python3
"""Build the Johnson Health Tech showroom theme from the supplied official artwork.

The source images stay in ``sources/``.  This program writes reviewable HTML/SVG
source under ``design/`` and asks local headless Chrome to rasterise that source.
It then encodes opaque, lossy WebP output for the data-only theme package.

Usage: python3 artwork/johnson-health-tech/build_theme.py
"""
from __future__ import annotations

import hashlib
from html import escape
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

from card_artwork import banner_svg, assert_all_geometry_unchanged


ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
SOURCES = ROOT / "sources"
DESIGN = ROOT / "design"
PACK = REPO / "theme-packs" / "johnson-health-tech"
WALLPAPERS = PACK / "wallpapers"
BANNERS = PACK / "banners"
REPORT = ROOT / "validation" / "build-report.json"
CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
CWEBP = Path("/opt/homebrew/bin/cwebp")
DWEBP = Path("/opt/homebrew/bin/dwebp")
WALLPAPER_SIZE = (1920, 1080)
BANNER_SIZE = (384, 216)
MASTER_BANNER = (768, 432)
CHROME_FLAGS = [
    "--headless", "--disable-gpu", "--hide-scrollbars", "--force-color-profile=srgb",
    "--force-device-scale-factor=1", "--default-background-color=202A31ff", "--no-first-run",
    "--no-default-browser-check", "--disable-extensions", "--disable-background-networking",
    "--disable-component-update", "--disable-sync", "--metrics-recording-only",
]

PRODUCTS = json.loads((ROOT / "scenes.json").read_text(encoding="utf-8"))

BANNER_IDS = (
    "cast", "bili", "live", "emby", "tencent", "player", "disk", "weather",
    "alarm", "voice", "light", "calendar", "gallery", "manager", "settings", "video",
)

# These are intentionally generic Android functions: the original package names still decide
# launch behavior, and unknown apps retain the launcher appearance.
APP_BANNERS = {
    "com.xiaomi.mitv.smartshare": "cast",
    "com.xiaodianshi.tv.yst": "bili",
    "com.zb.test": "live",
    "com.mb.android": "emby",
    "com.ktcp.video": "tencent",
    "com.xiaomi.mitv.mediaexplorer": "player",
    "com.yyw.box.androidclient": "disk",
    "com.xiaomi.tweather": "weather",
    "com.mitv.alarmcenter": "alarm",
    "com.xiaomi.voicecontrol": "voice",
    "com.xiaomi.mitv.light": "light",
    "com.xiaomi.mitv.calendar": "calendar",
    "com.xiaomi.tv.gallery": "gallery",
    "com.xiaomi.mitv.tvmanager": "manager",
    "com.xiaomi.mitv.settings": "settings",
    "com.duokan.videodaily": "video",
    "com.android.settings": "settings",
    "com.android.gallery3d": "gallery",
    "com.google.android.apps.photos": "gallery",
    "com.android.documentsui": "disk",
    "com.google.android.documentsui": "disk",
    "com.google.android.calendar": "calendar",
    "com.google.android.deskclock": "alarm",
    "com.android.tv.settings": "settings",
    "com.google.android.youtube": "video",
}


def require_tools() -> None:
    missing = [str(tool) for tool in (CHROME, CWEBP, DWEBP, Path("/usr/bin/sips")) if not os.access(tool, os.X_OK)]
    if missing:
        raise SystemExit("Required local renderer tools are unavailable: " + ", ".join(missing))


def run(command: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(command, check=True, capture_output=True, text=True, timeout=120, **kwargs)


def wallpaper_html(product: dict) -> str:
    """Compose an official scene and original logo shapes in the approved full-screen layout."""
    right = product["side"] == "right"
    edge, opposite, angle = ("right", "left", 270) if right else ("left", "right", 90)
    return f'''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><title>{escape(product['brand'])}</title>
<style>
*{{box-sizing:border-box}}html,body{{margin:0;width:1920px;height:1080px;overflow:hidden;background:#1A201D;color:#F9F7F1}}
body{{font-family:"PingFang SC",Arial,sans-serif}}
.scene{{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;object-position:{product['position']}}}
.scrim{{position:absolute;inset:0;background:linear-gradient({angle}deg,rgba(17,23,21,.8) 0%,rgba(17,23,21,.57) 21%,rgba(17,23,21,.16) 53%,rgba(17,23,21,.03) 79%),linear-gradient(0deg,rgba(18,24,21,.92),rgba(18,24,21,.6) 20%,transparent 45%,rgba(18,24,21,.13) 69%,rgba(18,24,21,.6))}}
.brand{{position:absolute;{edge}:120px;top:340px;width:348px}}
.jht{{display:block;width:172px;height:auto;opacity:.92}}
.brand-logo{{display:block;width:{product['logo_width']}px;max-height:123px;object-fit:contain;object-position:left center;filter:brightness(0) invert(1);margin-top:62px}}
.category{{font-size:27px;letter-spacing:8px;margin-top:28px;color:#EFEDE4}}
.company{{position:absolute;{opposite}:120px;bottom:363px;font-size:22px;letter-spacing:3px;color:rgba(255,255,255,.76)}}
</style><body>
<img class="scene" src="../sources/{escape(product['image'])}" alt=""><div class="scrim"></div>
<div class="brand"><img class="jht" src="../sources/logo-white.png" alt="JOHNSON">
<img class="brand-logo" src="../sources/{escape(product['logo'])}" alt="{escape(product['brand'])}">
<div class="category">{escape(product['category'])}</div></div>
<div class="company">乔山健康科技 · 健康 价值 共享</div>
</body></html>'''


def screenshot(page: Path, png: Path, size: tuple[int, int], profile: Path) -> None:
    run([str(CHROME), *CHROME_FLAGS, f"--user-data-dir={profile}", f"--window-size={size[0]},{size[1]}",
         f"--screenshot={png}", page.resolve().as_uri()])
    data = png.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", data[16:24]) != size:
        raise RuntimeError(f"Chrome did not render {page.name} at {size}")


def opaque_webp(webp: Path, expected: tuple[int, int], scratch: Path) -> bool:
    data = webp.read_bytes()
    if not (data[:4] == b"RIFF" and data[8:12] == b"WEBP" and data[12:16] == b"VP8 " and data[23:26] == b"\x9d\x01\x2a"):
        return False
    width, height = struct.unpack("<HH", data[26:30])
    if (width & 0x3FFF, height & 0x3FFF) != expected:
        return False
    pam = scratch / (webp.stem + ".pam")
    run([str(DWEBP), "-quiet", str(webp), "-pam", "-o", str(pam)])
    header, pixels = pam.read_bytes().split(b"ENDHDR\n", 1)
    fields = dict(line.split(b" ", 1) for line in header.splitlines()[1:])
    pam.unlink()
    return fields.get(b"DEPTH") == b"4" and len(pixels) == expected[0] * expected[1] * 4 and pixels[3::4].count(255) == expected[0] * expected[1]


def encode(png: Path, webp: Path, size: tuple[int, int], scratch: Path, report_path: Path) -> dict:
    staged = scratch / webp.name
    run([str(CWEBP), "-quiet", "-resize", str(size[0]), str(size[1]), "-q", "90", "-sharp_yuv", "-m", "6", "-noalpha", str(png), "-o", str(staged)])
    if not opaque_webp(staged, size, scratch):
        raise RuntimeError(f"WebP opacity or dimensions check failed: {webp.name}")
    shutil.copyfile(staged, webp)
    payload = webp.read_bytes()
    return {"file": str(report_path.relative_to(REPO)), "size": list(size), "bytes": len(payload),
            "sha256": hashlib.sha256(payload).hexdigest(), "opaque": True, "chunk": "VP8 "}


def pack_metadata() -> tuple[dict, dict]:
    """Produce the only two package JSON files from the same data that drives rendering."""
    manifest = {"schema": 1, "id": "johnson-health-tech", "name": "乔山 · 品牌展厅", "version": 1}
    config = {
        "header_style": "default", "background_color": "#1A201D", "wallpaper": True,
        "wallpaper_dim": 0, "wallpaper_true_color": True, "wallpaper_rotation_minutes": 2,
        "wallpaper_order": "sequential",
        "wallpaper_names": {f"{p['id']}.webp": f"{p['brand']} {p['category']}" for p in PRODUCTS},
        "wallpaper_tints": {f"{p['id']}.webp": p["tint"] for p in PRODUCTS},
        "app_banners": {package: f"banners/{ident}.webp" for package, ident in APP_BANNERS.items()},
        "columns": 6, "dock_columns": 6, "rows_below_dock": 0, "dock_row": True,
        "dock_title": False, "dock_even_padding": False, "always_show_names": True,
        "cinematic_home": False, "ambient_glow": False, "sky_intensity": 0,
    }
    return manifest, config


def publish(staged: Path, target: Path) -> None:
    """Copy into the target filesystem and then atomically replace one published file."""
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name("." + target.name + ".part")
    shutil.copyfile(staged, partial)
    os.replace(partial, target)


def remove_stale(folder: Path, pattern: str, expected: set[str]) -> None:
    """Delete only old generated names after every staged replacement has succeeded."""
    for generated in folder.glob(pattern):
        if generated.name not in expected:
            generated.unlink()


def main() -> None:
    require_tools()
    assert_all_geometry_unchanged()
    required_sources = {"logo-white.png"}
    required_sources.update(p[key] for p in PRODUCTS for key in ("image", "logo"))
    missing_sources = sorted(source for source in required_sources if not (SOURCES / source).is_file())
    if missing_sources:
        raise SystemExit("Missing official source images: " + ", ".join(missing_sources))
    with tempfile.TemporaryDirectory() as raw:
        scratch = Path(raw)
        render_root = scratch / "render"
        staged_design = render_root / "design"
        staged_design.mkdir(parents=True)
        # Wallpaper HTML deliberately uses ../sources just like its published review copy.
        # This symlink exists only while Chrome renders and is never published or packaged.
        (render_root / "sources").symlink_to(SOURCES, target_is_directory=True)
        staged_pack = scratch / "pack"
        staged_wallpapers = staged_pack / "wallpapers"
        staged_banners = staged_pack / "banners"
        staged_wallpapers.mkdir(parents=True)
        staged_banners.mkdir(parents=True)
        report_rows: list[dict] = []
        profile = scratch / "chrome-profile"
        for product in PRODUCTS:
            ident = product["id"]
            page = staged_design / f"{ident}.html"
            page.write_text(wallpaper_html(product), encoding="utf-8")
            png = scratch / f"{ident}.png"
            screenshot(page, png, WALLPAPER_SIZE, profile)
            name = f"{ident}.webp"
            report_rows.append(encode(png, staged_wallpapers / name, WALLPAPER_SIZE, scratch, WALLPAPERS / name))
        for ident in BANNER_IDS:
            svg = staged_design / f"banner-{ident}.svg"
            svg.write_text(banner_svg(ident), encoding="utf-8")
            png = scratch / f"banner-{ident}.png"
            screenshot(svg, png, MASTER_BANNER, profile)
            name = f"{ident}.webp"
            report_rows.append(encode(png, staged_banners / name, BANNER_SIZE, scratch, BANNERS / name))
        # Use dwebp plus Chrome to make an actual JPEG thumbnail of the staged first wallpaper.
        preview = staged_pack / "preview.jpg"
        png = scratch / "preview.png"
        run([str(DWEBP), "-quiet", str(staged_wallpapers / "01-matrix.webp"), "-o", str(png)])
        page = scratch / "preview.html"
        page.write_text(f'<!doctype html><style>html,body{{margin:0;overflow:hidden}}img{{width:960px;height:540px;display:block}}</style><img src="{png.as_uri()}">', encoding="utf-8")
        screenshot(page, scratch / "preview-render.png", (960, 540), scratch / "profile")
        # Chrome screenshots are PNG, and macOS supplies sips for a local JPEG conversion.
        subprocess.run(["/usr/bin/sips", "-s", "format", "jpeg", str(scratch / "preview-render.png"), "--out", str(preview)], check=True, capture_output=True, timeout=60)
        versions = {"chrome": run([str(CHROME), "--version"]).stdout.strip(), "cwebp": run([str(CWEBP), "-version"]).stdout.strip()}
        report = {"command": "python3 artwork/johnson-health-tech/build_theme.py", "renderer": versions,
                  "style": "fullscreen-soft-glow", "wallpapers": len(PRODUCTS), "banners": len(BANNER_IDS), "items": report_rows,
                  "preview": {"file": "theme-packs/johnson-health-tech/preview.jpg", "size": [960, 540],
                              "sha256": hashlib.sha256(preview.read_bytes()).hexdigest()}}
        staged_report = scratch / "build-report.json"
        staged_report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        manifest, config = pack_metadata()
        staged_manifest = staged_pack / "manifest.json"
        staged_config = staged_pack / "config.json"
        staged_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        staged_config.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        for product in PRODUCTS:
            ident = product["id"]
            publish(staged_wallpapers / f"{ident}.webp", WALLPAPERS / f"{ident}.webp")
            publish(staged_design / f"{ident}.html", DESIGN / f"{ident}.html")
        for ident in BANNER_IDS:
            publish(staged_banners / f"{ident}.webp", BANNERS / f"{ident}.webp")
            publish(staged_design / f"banner-{ident}.svg", DESIGN / f"banner-{ident}.svg")
        publish(preview, PACK / "preview.jpg")
        publish(staged_manifest, PACK / "manifest.json")
        publish(staged_config, PACK / "config.json")
        publish(staged_report, REPORT)

    remove_stale(WALLPAPERS, "*.webp", {f"{p['id']}.webp" for p in PRODUCTS})
    remove_stale(BANNERS, "*.webp", {f"{ident}.webp" for ident in BANNER_IDS})
    remove_stale(DESIGN, "0*.html", {f"{p['id']}.html" for p in PRODUCTS})
    remove_stale(DESIGN, "banner-*.svg", {f"banner-{ident}.svg" for ident in BANNER_IDS})
    print(f"Built {len(PRODUCTS)} wallpapers and {len(BANNER_IDS)} opaque banners.")


if __name__ == "__main__":
    main()
