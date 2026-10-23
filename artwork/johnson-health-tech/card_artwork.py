"""Johnson Health Tech soft-glow banner SVGs.

Inherited cards retint only their first background gradient, then use the
shared VectorDrawable-to-SVG exporter. ``disk`` is the sole geometry exception:
the old source was a 115 numeral, so it uses the approved generic folder glyph.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
SOFT_GLOW = ROOT.parent / "soft-tv-icons" / "soft-glow"
ANDROID = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"
_EXPORT_PACK_PATH = ROOT.parent / "soft-tv-icons" / "export_pack.py"
_export_spec = importlib.util.spec_from_file_location("johnson_soft_glow_export", _EXPORT_PACK_PATH)
assert _export_spec and _export_spec.loader
_export_pack = importlib.util.module_from_spec(_export_spec)
_export_spec.loader.exec_module(_export_pack)

IDS = (
    "cast", "bili", "live", "emby", "tencent", "player", "disk", "weather",
    "alarm", "voice", "light", "calendar", "gallery", "manager", "settings", "video",
)
PALETTES = (
    ("#ADB8B6", "#748B89"), ("#B99699", "#865E66"),
    ("#CDBB9C", "#A08A6D"), ("#B4BDAA", "#7F9178"),
    ("#ADB8C5", "#7A8C9E"), ("#C4A79C", "#9A796E"),
)
# Utility cards cycle the same six calm hues. Disk is deliberately smoke-blue.
BACKGROUND = {ident: PALETTES[index % len(PALETTES)] for index, ident in enumerate(IDS)}
BACKGROUND["disk"] = PALETTES[4]
ACCENT_REMAP = {
    "emby": {"#57A878": "#7F9178"},
    "player": {"#6797D7": "#9A796E"},
}
FOLDER_PATH = "M4 5H9.5L12 7.5H20C21.1 7.5 22 8.4 22 9.5V18C22 19.1 21.1 20 20 20H4C2.9 20 2 19.1 2 18V7C2 5.9 2.9 5 4 5Z"
_TRANSFORM = re.compile(r"translate\(([^ ]+) ([^)]+)\) scale\(([^ ]+) ([^)]+)\)")


def _source_svg(ident: str) -> str:
    """Retint an in-memory source copy and delegate conversion to export_pack.svg."""
    source = SOFT_GLOW / f"banner_soft_{ident}.xml"
    root = ET.parse(source).getroot()
    background = root.find("path")
    assert background is not None and background.get(ANDROID + "pathData") == "M0,0H320V180H0Z"
    fill = background.find(f"{AAPT}attr[@name='android:fillColor']")
    assert fill is not None and len(fill) == 1 and fill[0].tag == "gradient"
    fill[0].set(ANDROID + "startColor", BACKGROUND[ident][0])
    fill[0].set(ANDROID + "endColor", BACKGROUND[ident][1])
    for node in root.iter():
        for attribute in (ANDROID + "fillColor", ANDROID + "strokeColor"):
            value = node.get(attribute)
            replacement = ACCENT_REMAP.get(ident, {}).get(value.upper()) if value else None
            if replacement:
                node.set(attribute, replacement)
    with tempfile.TemporaryDirectory() as temporary:
        staged = Path(temporary) / source.name
        ET.ElementTree(root).write(staged, encoding="utf-8", xml_declaration=True)
        return _export_pack.svg(staged)


def _disk_svg() -> str:
    """The approved generic-folder exception, lifted with soft-glow's shadows."""
    start, end = BACKGROUND["disk"]
    layers = (("42.2", "0.03137"), ("40.400000000000006", "0.06667"), ("38.6", "0.0902"))
    shadows = "".join(
        f'<g transform="translate(107.2 {y}) scale(4.4 4.4)"><path d="{FOLDER_PATH}" fill="#0D2744" fill-opacity="{opacity}"/></g>'
        for y, opacity in layers
    )
    front = f'<g transform="translate(107.2 37.2) scale(4.4 4.4)"><path d="{FOLDER_PATH}" fill="url(#g2)" fill-opacity="1.0"/></g>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="768" height="432" viewBox="0 0 320 180"><defs><linearGradient id="g0" gradientUnits="userSpaceOnUse" x1="45.0" y1="0.0" x2="275.0" y2="190.0"><stop offset="0" stop-color="{start}" stop-opacity="1.0"/><stop offset="1" stop-color="{end}" stop-opacity="1.0"/></linearGradient><radialGradient id="g1" gradientUnits="userSpaceOnUse" cx="80.0" cy="-20.0" r="280.0"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.15686"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0.0"/></radialGradient><linearGradient id="g2" gradientUnits="userSpaceOnUse" x1="5.0" y1="2.0" x2="19.0" y2="24.0"><stop offset="0" stop-color="#FFFFFF" stop-opacity="1.0"/><stop offset="1" stop-color="#DCE8EE" stop-opacity="1.0"/></linearGradient></defs><path d="M0,0H320V180H0Z" fill="url(#g0)" fill-opacity="1"/><path d="M0,0H320V180H0Z" fill="url(#g1)" fill-opacity="1"/>{shadows}{front}</svg>'


def banner_svg(ident: str) -> str:
    """Return the 768x432 SVG card for one supported application id."""
    if ident not in IDS:
        raise ValueError(f"unknown banner id: {ident}")
    return _disk_svg() if ident == "disk" else _source_svg(ident)


def _source_geometry(ident: str) -> tuple[list[tuple[str, str | None]], list[tuple[float, float, float, float]]]:
    root = ET.parse(SOFT_GLOW / f"banner_soft_{ident}.xml").getroot()
    paths: list[tuple[str, str | None]] = []
    groups: list[tuple[float, float, float, float]] = []
    def walk(node: ET.Element) -> None:
        for child in node:
            if child.tag == "group":
                groups.append(tuple(float(child.get(ANDROID + key, default)) for key, default in (("translateX", "0"), ("translateY", "0"), ("scaleX", "1"), ("scaleY", "1"))))
                walk(child)
            elif child.tag == "path":
                paths.append((child.get(ANDROID + "pathData"), child.get(ANDROID + "strokeWidth")))
    walk(root)
    return paths, groups


def assert_geometry_unchanged(ident: str) -> None:
    """Verify inherited pathData, transforms, and stroke widths; disk is exempt."""
    if ident == "disk":
        return
    expected_paths, expected_groups = _source_geometry(ident)
    root = ET.fromstring(banner_svg(ident))
    actual_paths = [(node.get("d"), node.get("stroke-width")) for node in root.iter() if node.tag.rsplit("}", 1)[-1] == "path"]
    actual_groups = []
    for node in root.iter():
        if node.tag.rsplit("}", 1)[-1] == "g":
            match = _TRANSFORM.fullmatch(node.get("transform", ""))
            assert match, node.get("transform")
            actual_groups.append(tuple(float(value) for value in match.groups()))
    assert actual_paths == expected_paths, ident
    assert actual_groups == expected_groups, ident


def assert_all_geometry_unchanged() -> None:
    """Check every inherited card; disk is the only documented new path."""
    for ident in IDS:
        assert_geometry_unchanged(ident)
