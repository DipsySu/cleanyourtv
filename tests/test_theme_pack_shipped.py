"""Packs every folder under theme-packs/ exactly as a release would, so a broken shipped theme fails CI.

tools/theme_pack.py checks names, sizes, references and font signatures, but it reads no image, so it cannot refuse
what the launcher's import refuses after a bounds decode. This test runs the tool over the real packs and then reads
the ZIP the way the TV will: every image must have a header Android can size, inside the bounds the launcher decodes
(4096 px for banners, 1024 px for coin icons), and every banner's #cell or #x,y,width,height must fit its image
(ThemeArtReference.fits), or the import refuses the whole pack. Names, tints and coins must point at wallpapers the
pack really carries, and header_style and wallpaper_order must be values the launcher knows, since it silently
ignores a key that matches no file and falls back to its defaults for an unknown value. Every banner, icon and font
must be referenced too: the TV keeps whatever the ZIP holds, and nothing draws an unreferenced one.

Run from the repository root: python3 -m unittest discover -s tests -p 'test_theme_pack*.py' -v
"""
import importlib.util
import json
from pathlib import Path
import re
import struct
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "theme_pack.py"
SPEC = importlib.util.spec_from_file_location("theme_pack_shipped_tool", TOOL)
THEME_PACK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(THEME_PACK)
PACKS_DIR = ROOT / "theme-packs"
TINT_RE = re.compile(r"#[0-9A-Fa-f]{6}\Z")
# ThemeArtReference.safeBounds for banners; the coin decoder's own limit for icons.
MAX_BANNER_SIDE, MAX_BANNER_PIXELS, MAX_ICON_SIDE = 4096, 16 * 1024 * 1024, 1024
JPEG_SOF = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}
# HeaderStyle.parse and WallpaperRotation.validOrder: anything else quietly becomes the default header or shuffle.
HEADER_STYLES = {"default", "odyssey", "animal_crossing", "zelda"}
WALLPAPER_ORDERS = {"sequential", "shuffle"}
ATLAS_COLUMNS = 4


def image_size(data):
    """(width, height) from a PNG, WebP or baseline/progressive JPEG header, or None when it is not one of them."""
    if data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR" and len(data) >= 24:
        return struct.unpack(">II", data[16:24])
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        chunk = data[12:16]
        if chunk == b"VP8 " and data[23:26] == b"\x9d\x01\x2a" and len(data) >= 30:
            width, height = struct.unpack("<HH", data[26:30])
            return width & 0x3FFF, height & 0x3FFF
        if chunk == b"VP8L" and data[20:21] == b"\x2f" and len(data) >= 25:
            bits = int.from_bytes(data[21:25], "little")
            return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
        if chunk == b"VP8X" and len(data) >= 30:
            return int.from_bytes(data[24:27], "little") + 1, int.from_bytes(data[27:30], "little") + 1
        return None
    if data[:2] == b"\xff\xd8":
        at = 2
        while at + 4 <= len(data):
            if data[at] != 0xFF:
                return None
            marker = data[at + 1]
            if marker == 0xFF:  # Fill byte before a marker.
                at += 1
                continue
            if marker in JPEG_SOF and at + 9 <= len(data):
                height, width = struct.unpack(">HH", data[at + 5:at + 9])
                return width, height
            if marker in (0x01, 0xD8) or 0xD0 <= marker <= 0xD7:
                at += 2
                continue
            if marker in (0xD9, 0xDA):  # End of image, or scan data before any frame header.
                return None
            at += 2 + struct.unpack(">H", data[at + 2:at + 4])[0]
    return None


def banner_fits(reference, width, height):
    """ThemeArtReference.Value.fits: whether a banner's suffix fits its width x height image, as the TV's import checks.

    A #cell needs a 4x4 atlas of at least one pixel a cell, and a #x,y,width,height must lie inside the image. The tool
    has already checked the suffix's shape and its 4096 px limit, so anything else here is a malformed reference.
    """
    _, marker, suffix = reference.partition("#")
    if not marker:
        return True
    if THEME_PACK.ATLAS_CELL_RE.fullmatch(suffix):
        return width >= ATLAS_COLUMNS and height >= ATLAS_COLUMNS
    rect = THEME_PACK.ATLAS_RECT_RE.fullmatch(suffix)
    if not rect:
        return False
    x, y, rect_width, rect_height = (int(part) for part in rect.groups())
    return x + rect_width <= width and y + rect_height <= height


def shipped_packs():
    if not PACKS_DIR.is_dir():
        return []
    return sorted(folder for folder in PACKS_DIR.iterdir() if folder.is_dir() and not folder.is_symlink())


def mapping(value):
    """Object or `key=value` array, as Config merges them; the tool has already checked the shape."""
    if isinstance(value, dict):
        return dict(value)
    return dict(item.split("=", 1) for item in value or [])


class ShippedThemePackTest(unittest.TestCase):
    def test_image_size_reads_the_formats_packs_use(self):
        png = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\x0dIHDR" + struct.pack(">II", 384, 216)
        vp8 = b"RIFF\x00\x00\x00\x00WEBPVP8 \x00\x00\x00\x00" + b"\x00\x00\x00\x9d\x01\x2a" + struct.pack("<HH", 384, 216)
        vp8l = b"RIFF\x00\x00\x00\x00WEBPVP8L\x00\x00\x00\x00\x2f" + (127 | (127 << 14)).to_bytes(4, "little") + b"\x00"
        vp8x = b"RIFF\x00\x00\x00\x00WEBPVP8X\x00\x00\x00\x00\x10\x00\x00\x00" + (127).to_bytes(3, "little") * 2
        jpeg = b"\xff\xd8\xff\xe0\x00\x04ab\xff\xff\xc2\x00\x11\x08" + struct.pack(">HH", 1080, 1920) + b"\x03"
        self.assertEqual(image_size(png), (384, 216))
        self.assertEqual(image_size(vp8), (384, 216))
        self.assertEqual(image_size(vp8l), (128, 128))
        self.assertEqual(image_size(vp8x), (128, 128))
        self.assertEqual(image_size(jpeg), (1920, 1080))
        for junk in (b"", b"GIF89a", b"\xff\xd8\xff\xd9", b"RIFF\x00\x00\x00\x00WEBPVP8 " + b"\x00" * 20):
            self.assertIsNone(image_size(junk))

    def test_banner_fits_matches_the_launchers_bounds_check(self):
        self.assertTrue(banner_fits("banners/card.webp", 1, 1))
        self.assertTrue(banner_fits("banners/atlas.png#15", 4, 4))
        self.assertFalse(banner_fits("banners/atlas.png#0", 3, 128))
        self.assertFalse(banner_fits("banners/atlas.png#0", 128, 3))
        self.assertTrue(banner_fits("banners/island.webp#26,359,315,310", 1254, 1254))
        self.assertTrue(banner_fits("banners/island.webp#0,0,1254,1254", 1254, 1254))
        self.assertFalse(banner_fits("banners/island.webp#1,0,1254,1254", 1254, 1254))
        self.assertFalse(banner_fits("banners/island.webp#1100,1100,900,900", 1254, 1254))
        self.assertFalse(banner_fits("banners/island.webp#1, 2,3,4", 1254, 1254))

    def test_a_pack_the_tv_would_refuse_or_misread_fails(self):
        png = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\x0dIHDR" + struct.pack(">II", 384, 216)
        good = {"app_banners": {"com.example.app": "banners/card.png#284,0,100,216"},
                "header_style": "odyssey", "wallpaper_order": "sequential"}
        broken = (({"app_banners": {"com.example.app": "banners/card.png#285,0,100,216"}}, "outside its 384x216"),
                  ({"app_banners": ["com.example.app=banners/card.png#0,0,384,217"]}, "outside its 384x216"),
                  ({"header_style": "odysey"}, "default header"),
                  ({"wallpaper_order": "shufle"}, "shuffle instead"))
        for config, failure in ((good, None),) + broken:
            with self.subTest(config=config), tempfile.TemporaryDirectory() as raw:
                folder = Path(raw) / "card-test"
                (folder / "banners").mkdir(parents=True)
                (folder / "banners" / "card.png").write_bytes(png)
                (folder / "manifest.json").write_text(json.dumps(
                    {"schema": 1, "id": "card-test", "name": "Card test", "version": 1}))
                if "app_banners" not in config:
                    config = dict(config, app_banners={"com.example.app": "banners/card.png"})
                (folder / "config.json").write_text(json.dumps(config))
                archive = Path(raw) / "card-test.zip"
                details = THEME_PACK.pack(folder, archive)
                if failure is None:
                    self.check_archive("card-test", details, archive)
                else:
                    with self.assertRaisesRegex(AssertionError, failure):
                        self.check_archive("card-test", details, archive)

    def test_every_shipped_pack_packs_and_reads_like_the_tv(self):
        packs = shipped_packs()
        if not packs:
            self.skipTest("no theme-packs folders")
        for folder in packs:
            with self.subTest(pack=folder.name), tempfile.TemporaryDirectory() as raw:
                archive = Path(raw) / (folder.name + ".zip")
                try:
                    details = THEME_PACK.pack(folder, archive)
                except THEME_PACK.ThemeError as error:
                    self.fail(f"{folder.name}: {error}")
                self.check_archive(folder.name, details, archive)

    def check_archive(self, name, details, archive):
        # The README and the install commands use the folder name as the theme id.
        self.assertEqual(details["manifest"]["id"], name)
        with zipfile.ZipFile(archive) as bundle:
            payloads = {entry: bundle.read(entry) for entry in bundle.namelist()}
        config = json.loads(payloads["config.json"])

        sizes = {}
        for entry, data in payloads.items():
            folder = entry.split("/", 1)[0] if "/" in entry else ""
            if folder in ("wallpapers", "banners", "icons") or entry in ("wallpaper.jpg", "preview.jpg"):
                size = image_size(data)
                self.assertIsNotNone(size, f"{name}: {entry} is not a PNG, WebP or JPEG Android can size")
                sizes[entry] = size
                width, height = size
                self.assertTrue(width > 0 and height > 0, f"{name}: {entry} has no pixels")
                if folder == "banners":
                    self.assertTrue(width <= MAX_BANNER_SIDE and height <= MAX_BANNER_SIDE
                                    and width * height <= MAX_BANNER_PIXELS, f"{name}: {entry} is {width}x{height}")
                if folder == "icons":
                    self.assertTrue(width <= MAX_ICON_SIDE and height <= MAX_ICON_SIDE,
                                    f"{name}: {entry} is {width}x{height}")

        wallpapers = {entry.split("/", 1)[1] for entry in payloads if entry.startswith("wallpapers/")}
        if "wallpaper.jpg" in payloads:
            wallpapers.add("wallpaper.jpg")
        for key in ("wallpaper_names", "wallpaper_tints", "wallpaper_coins"):
            for wallpaper in mapping(config.get(key)):
                self.assertIn(wallpaper, wallpapers, f"{name}: {key} names a wallpaper the pack does not carry")
        for wallpaper, tint in mapping(config.get("wallpaper_tints")).items():
            self.assertRegex(tint, TINT_RE, f"{name}: tint for {wallpaper}")
        if "clock_font_axes" in config:
            self.assertIn("clock_font", config, f"{name}: clock_font_axes without clock_font does nothing")
        if "header_style" in config:
            self.assertIn(config["header_style"], HEADER_STYLES, f"{name}: the launcher would draw the default header")
        if "wallpaper_order" in config:
            self.assertIn(config["wallpaper_order"], WALLPAPER_ORDERS, f"{name}: the launcher would shuffle instead")
        for app, reference in mapping(config.get("app_banners")).items():
            width, height = sizes[reference.split("#", 1)[0]]
            self.assertTrue(banner_fits(reference, width, height),
                            f"{name}: {app} frames {reference} outside its {width}x{height} image; the TV refuses the pack")

        referenced = {path.split("#", 1)[0] for path in mapping(config.get("app_banners")).values()}
        referenced |= set(mapping(config.get("wallpaper_coins")).values())
        if config.get("clock_font"):
            referenced.add(config["clock_font"])
        unused = sorted(entry for entry in payloads
                        if entry.split("/", 1)[0] in ("banners", "icons", "fonts") and entry not in referenced)
        self.assertEqual(unused, [], f"{name}: files nothing in config.json draws")


if __name__ == "__main__":
    unittest.main()
