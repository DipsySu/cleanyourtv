"""The fonts/ and icons/ rules of tools/theme_pack.py, which must agree with the launcher's import checks.

ThemePackage, ThemeStore and ThemeFont (launcher/app/src/main/java/com/gathin/tvhome) apply the same rules on the
TV; these cases pin the host side so a pack the TV would refuse never leaves the computer. Fonts here are a few
bytes with the right sfnt tag: the tool checks the tag, not the tables, exactly like the launcher's import.

Run from the repository root: python3 -m unittest discover -s tests -p 'test_theme_pack*.py' -v
"""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "theme_pack.py"
SPEC = importlib.util.spec_from_file_location("theme_pack_assets_tool", TOOL)
THEME_PACK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(THEME_PACK)
ThemeError = THEME_PACK.ThemeError

TRUETYPE = b"\x00\x01\x00\x00" + b"\x00" * 60
MANIFEST = {"schema": 1, "id": "clock-test", "name": "Clock Test", "version": 1}


class ThemePackAssetsTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.theme = self.root / "theme"
        for folder in ("fonts", "icons", "wallpapers"):
            (self.theme / folder).mkdir(parents=True)
        (self.theme / "manifest.json").write_text(json.dumps(MANIFEST))
        (self.theme / "fonts" / "clock.ttf").write_bytes(TRUETYPE)
        (self.theme / "icons" / "coin-cap.webp").write_bytes(b"RIFF....WEBP")
        (self.theme / "wallpapers" / "01-cap.jpg").write_bytes(b"\xff\xd8\xff")
        self.config({})

    def config(self, config):
        (self.theme / "config.json").write_text(json.dumps(config))

    def pack(self):
        return THEME_PACK.pack(self.theme, self.root / "out.zip")

    def assert_config_rejected(self, key, value):
        self.config({key: value})
        with self.assertRaises(ThemeError) as caught:
            self.pack()
        self.assertEqual(str(caught.exception), "invalid value for config key: " + key)

    def zip_with(self, entries, config=None):
        archive = self.root / "direct.zip"
        with zipfile.ZipFile(archive, "w") as bundle:
            bundle.writestr("manifest.json", json.dumps(MANIFEST))
            bundle.writestr("config.json", json.dumps(config or {}))
            for name, data in entries:
                bundle.writestr(name, data)
        return archive

    # Folder and file names

    def test_fonts_and_icons_are_pack_folders_one_level_deep(self):
        for name in ("fonts/", "icons/"):
            self.assertTrue(THEME_PACK.allowed_path(name, True), name)
        for name in ("fonts/clock.ttf", "fonts/Clock-Bold.OTF", "fonts/a_b.c.ttf", "icons/coin.webp",
                     "icons/Coin.PNG", "icons/c.jpeg"):
            self.assertTrue(THEME_PACK.allowed_path(name), name)
        for name in ("fonts/clock.woff", "fonts/clock.woff2", "fonts/clock.ttc", "fonts/clock.png", "icons/coin.ttf",
                     "icons/coin.svg", "fonts/x/clock.ttf", "fonts//clock.ttf", "fonts/./clock.ttf", "fonts/a..ttf",
                     "fonts/.clock.ttf", "fonts/-clock.ttf", "coins/coin.webp", "coins/", "fonts", "icons",
                     "clock.ttf", "../fonts/clock.ttf"):
            self.assertFalse(THEME_PACK.allowed_path(name), name)

    def test_case_insensitive_names_stay_ascii_like_java(self):
        # U+212A (Kelvin sign) folds to "k" under Unicode IGNORECASE; Java's CASE_INSENSITIVE is ASCII only.
        self.assertFalse(THEME_PACK.allowed_path("banners/\u212a.png"))
        self.assertFalse(THEME_PACK.allowed_path("fonts/\u212a.ttf"))
        self.assertFalse(THEME_PACK.allowed_path("icons/\u212a.webp"))

    def test_zip_accepts_fonts_and_icons_directory_entries(self):
        archive = self.zip_with([("fonts/", b""), ("icons/", b""), ("fonts/clock.ttf", TRUETYPE)])
        self.assertIn("fonts/clock.ttf", THEME_PACK.inspect_zip(archive)["entries"])
        archive = self.zip_with([("coins/", b"")])
        with self.assertRaisesRegex(ThemeError, "unsafe ZIP directory"):
            THEME_PACK.inspect_zip(archive)

    def test_finder_metadata_is_ignored_but_only_by_its_exact_name(self):
        (self.theme / ".DS_Store").write_bytes(b"\x00\x00\x00\x01Bud1")
        (self.theme / "icons" / ".DS_Store").write_bytes(b"\x00\x00\x00\x01Bud1")
        self.assertNotIn(".DS_Store", " ".join(self.pack()["entries"]))
        (self.theme / "icons" / "._coin-cap.webp").write_bytes(b"\x00\x05\x16\x07")
        with self.assertRaisesRegex(ThemeError, "unsupported source file: icons/._coin-cap.webp"):
            self.pack()

    def test_a_symlinked_finder_file_is_still_a_symlink(self):
        (self.theme / ".DS_Store").symlink_to(self.theme / "manifest.json")
        with self.assertRaisesRegex(ThemeError, "symbolic links"):
            self.pack()

    def test_nested_font_or_icon_folders_are_refused(self):
        (self.theme / "fonts" / "extra").mkdir()
        with self.assertRaisesRegex(ThemeError, "unsupported source directory: fonts/extra"):
            self.pack()

    # Font content and size

    def test_every_font_needs_a_single_face_sfnt_tag(self):
        for tag in (b"\x00\x01\x00\x00", b"OTTO", b"true"):
            (self.theme / "fonts" / "clock.ttf").write_bytes(tag + b"\x00" * 12)
            self.assertIn("fonts/clock.ttf", self.pack()["entries"])
        # The clock does not name this font, yet a collection, a web font or a stray file still fails the pack.
        for data in (b"ttcf" + b"\x00" * 12, b"wOFF" + b"\x00" * 12, b"wOF2" + b"\x00" * 12, b"otto" + b"\x00" * 12,
                     b"\x00\x01\x00\x01" + b"\x00" * 12, b"<svg></svg>", b"OTT", b""):
            (self.theme / "fonts" / "spare.otf").write_bytes(data)
            with self.subTest(data=data[:4]), \
                    self.assertRaisesRegex(ThemeError, "not a single TrueType or OpenType font: fonts/spare.otf"):
                self.pack()

    def test_font_files_are_capped_at_two_mebibytes(self):
        (self.theme / "fonts" / "clock.ttf").write_bytes(TRUETYPE[:4] + b"\x00" * (THEME_PACK.MAX_FONT - 4))
        self.pack()
        (self.theme / "fonts" / "clock.ttf").write_bytes(TRUETYPE[:4] + b"\x00" * (THEME_PACK.MAX_FONT - 3))
        with self.assertRaisesRegex(ThemeError, "source file too large: fonts/clock.ttf"):
            self.pack()

    def test_zip_font_over_the_limit_is_refused_before_it_is_read(self):
        big = TRUETYPE[:4] + b"\x00" * THEME_PACK.MAX_FONT
        with self.assertRaisesRegex(ThemeError, "ZIP entry too large: fonts/big.ttf"):
            THEME_PACK.inspect_zip(self.zip_with([("fonts/big.ttf", big)]))
        # The same bytes are an ordinary 2 MiB image under banners/, whose limit stays 8 MiB.
        THEME_PACK.inspect_zip(self.zip_with([("banners/big.png", big)]))

    def test_zip_font_with_a_web_font_tag_is_refused(self):
        with self.assertRaisesRegex(ThemeError, "not a single TrueType or OpenType font: fonts/web.ttf"):
            THEME_PACK.inspect_zip(self.zip_with([("fonts/web.ttf", b"wOF2" + b"\x00" * 12)]))

    # clock_font

    def test_clock_font_names_a_font_in_the_pack(self):
        self.config({"clock_font": "fonts/clock.ttf"})
        self.pack()
        (self.theme / "fonts" / "Clock.OTF").write_bytes(b"OTTO" + b"\x00" * 12)
        self.config({"clock_font": "fonts/Clock.OTF"})
        self.pack()

    def test_clock_font_must_be_a_real_pack_font_path(self):
        (self.theme / "icons" / "clock.png").write_bytes(b"\x89PNG")
        for value in ("", "fonts/missing.ttf", "fonts/clock.TTF", "clock.ttf", "/fonts/clock.ttf", "fonts/clock.ttf#0",
                      "fonts/../fonts/clock.ttf", "fonts/x/clock.ttf", "icons/clock.png", "banners/clock.ttf",
                      "fonts/clock.ttf ", " fonts/clock.ttf", "fonts\\clock.ttf", "@font/fredoka_bold", None, 1,
                      True, ["fonts/clock.ttf"], {"path": "fonts/clock.ttf"}):
            with self.subTest(value=value):
                self.assert_config_rejected("clock_font", value)

    # clock_font_axes

    def test_axes_turn_into_the_android_variation_string(self):
        self.assertEqual(THEME_PACK.axis_settings({"wght": 700, "wdth": 120}), "'wght' 700, 'wdth' 120")
        self.assertEqual(THEME_PACK.axis_settings(["wght=700", "wdth=120"]), "'wght' 700, 'wdth' 120")
        self.assertEqual(THEME_PACK.axis_settings(["wdth=120", "wght=700"]), "'wdth' 120, 'wght' 700")
        self.assertEqual(THEME_PACK.axis_settings(["slnt=-10", "GRAD=0"]), "'slnt' -10, 'GRAD' 0")
        # Same as ThemeFont.axes: leading zeros and -0 are whole numbers; nothing and none are the default instance.
        self.assertEqual(THEME_PACK.axis_settings(["wght=0700", "wdth=-0"]), "'wght' 700, 'wdth' 0")
        self.assertEqual(THEME_PACK.axis_settings({}), "")
        self.assertEqual(THEME_PACK.axis_settings([]), "")
        self.assertEqual(THEME_PACK.axis_settings({"wght": -10000, "wdth": 10000}), "'wght' -10000, 'wdth' 10000")
        eight = {tag: 1 for tag in ("wght", "wdth", "slnt", "ital", "opsz", "GRAD", "XTRA", "YOPQ")}
        self.assertEqual(THEME_PACK.axis_settings(eight).count("'"), 16)

    def test_invalid_axes_are_refused_whole(self):
        nine = {tag: 1 for tag in ("wght", "wdth", "slnt", "ital", "opsz", "GRAD", "XTRA", "YOPQ", "YTUC")}
        for value in (nine, [f"{tag}=1" for tag in nine], {"wght": True}, {"wght": 700.0}, {"wght": 700.5},
                      {"wght": "700"}, {"wght": None}, {"wght": [700]}, {"wgt": 700}, {"wghtt": 700},
                      {"wgh1": 700}, {"w ht": 700}, {"wght": 10001}, {"wght": -10001}, {"\u212aght": 700},
                      ["wght=700", "wght=400"], ["wght"], ["wght=7=0"], ["=700"], ["wght="], ["wght=+700"],
                      ["wght= 700"], ["wght =700"], ["wght=700 "], ["wght=7e2"], ["wght=0x2bc"], ["wght=7_00"],
                      ["wght=\u0667\u0660\u0660"], ["wght=1" + "0" * 4400], [700], [None], "wght=700", 700, None, True):
            with self.subTest(value=repr(value)[:60]):
                self.assertIsNone(THEME_PACK.axis_settings(value))
        self.assert_config_rejected("clock_font_axes", {"wght": 700.0})
        self.assert_config_rejected("clock_font_axes", "'wght' 700")

    def test_axes_are_a_pack_visual_key(self):
        self.config({"clock_font": "fonts/clock.ttf", "clock_font_axes": {"wght": 700, "wdth": 120}})
        self.pack()
        self.config({"clock_font_axes": ["wght=700"]})
        self.pack()

    # wallpaper_coins

    def test_coins_name_icon_files_in_the_pack(self):
        (self.theme / "icons" / "coin-moon.PNG").write_bytes(b"\x89PNG")
        # ThemeStore checks only the values; a key no wallpaper has (even "" or a repeat) is never looked up.
        for value in ({"01-cap.jpg": "icons/coin-cap.webp", "08-moon.jpg": "icons/coin-moon.PNG"},
                      ["01-cap.jpg=icons/coin-cap.webp"], {}, [], {"": "icons/coin-cap.webp"},
                      ["=icons/coin-cap.webp", "01-cap.jpg=icons/coin-cap.webp", "01-cap.jpg=icons/coin-moon.PNG"]):
            with self.subTest(value=value):
                self.config({"wallpaper_coins": value})
                self.pack()

    def test_coins_refuse_resource_names_and_unsafe_paths(self):
        (self.theme / "banners").mkdir()
        (self.theme / "banners" / "coin.webp").write_bytes(b"RIFF")
        for path in ("coin_odyssey_cap", "ic_odyssey_coin", "@drawable/coin_odyssey_cap", "", "icons/missing.webp",
                     "icons/coin-cap.webp#0", "icons/coin-cap.webp#0,0,8,8", "icons/../icons/coin-cap.webp",
                     "icons/x/coin-cap.webp", "/icons/coin-cap.webp", "banners/coin.webp", "fonts/clock.ttf",
                     "icons/coin-cap.WEBP", "icons/coin-cap.webp ", "wallpapers/01-cap.jpg"):
            with self.subTest(path=path):
                self.assert_config_rejected("wallpaper_coins", {"01-cap.jpg": path})

    def test_coin_mapping_shape_is_checked(self):
        for value in ("01-cap.jpg=icons/coin-cap.webp", ["01-cap.jpg"], ["01-cap.jpg=icons/a.webp=x"],
                      ["01-cap.jpg=icons/coin-cap.webp", "02-metro.jpg"], {"01-cap.jpg": 1},
                      {"01-cap.jpg": None}, [1], [None], 1, None, True):
            with self.subTest(value=value):
                self.assert_config_rejected("wallpaper_coins", value)

    def test_referenced_icons_are_capped_at_one_mebibyte(self):
        self.config({"wallpaper_coins": {"01-cap.jpg": "icons/coin-cap.webp"}})
        (self.theme / "icons" / "coin-cap.webp").write_bytes(b"R" * THEME_PACK.MAX_ICON)
        self.pack()
        (self.theme / "icons" / "coin-cap.webp").write_bytes(b"R" * (THEME_PACK.MAX_ICON + 1))
        self.assert_config_rejected("wallpaper_coins", {"01-cap.jpg": "icons/coin-cap.webp"})
        (self.theme / "icons" / "coin-cap.webp").write_bytes(b"")
        self.assert_config_rejected("wallpaper_coins", {"01-cap.jpg": "icons/coin-cap.webp"})

    def test_an_unreferenced_icon_only_meets_the_ordinary_file_limit(self):
        # Android checks icon size where config references one; extraction caps any image at 8 MiB.
        (self.theme / "icons" / "spare.webp").write_bytes(b"R" * (THEME_PACK.MAX_ICON + 1))
        self.pack()

    def test_names_and_tints_keep_their_loose_shape(self):
        self.config({"wallpaper_names": {"01-cap.jpg": "Cap Kingdom"}, "wallpaper_tints": ["01-cap.jpg=#454C87"]})
        self.pack()


if __name__ == "__main__":
    unittest.main()
