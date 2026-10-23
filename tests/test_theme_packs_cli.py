import json
import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "theme_pack.py"
SPEC = importlib.util.spec_from_file_location("theme_pack", TOOL)
THEME_PACK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(THEME_PACK)


class ThemePackCliTest(unittest.TestCase):
    def make_theme(self, root):
        theme = root / "theme"
        (theme / "banners").mkdir(parents=True)
        (theme / "manifest.json").write_text(json.dumps({"schema": 1, "id": "quiet-forest", "name": "Quiet Forest", "version": 2}))
        (theme / "config.json").write_text(json.dumps({"background_color": "#123456", "app_banners": {"com.example.app": "banners/a.png#2"}}))
        (theme / "banners" / "a.png").write_bytes(b"png")
        return theme

    def run_tool(self, *args):
        return subprocess.run([sys.executable, str(TOOL), *map(str, args)], text=True, capture_output=True)

    def test_pack_is_repeatable_and_inspectable(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw); theme = self.make_theme(root); first, second = root / "one.zip", root / "two.zip"
            self.assertEqual(self.run_tool("pack", theme, first).returncode, 0)
            self.assertEqual(self.run_tool("pack", theme, second).returncode, 0)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            inspected = self.run_tool("inspect", first)
            self.assertEqual(inspected.returncode, 0, inspected.stderr)
            self.assertIn("quiet-forest v2", inspected.stdout)

    def test_pack_rejects_unknown_and_symlink_source_files(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw); theme = self.make_theme(root); (theme / "README.md").write_text("no")
            result = self.run_tool("pack", theme, root / "bad.zip")
            self.assertNotEqual(result.returncode, 0); self.assertIn("unsupported source file", result.stderr)
            (theme / "README.md").unlink(); (theme / "linked.png").symlink_to(theme / "banners" / "a.png")
            result = self.run_tool("pack", theme, root / "bad.zip")
            self.assertNotEqual(result.returncode, 0); self.assertIn("symbolic links", result.stderr)

    def test_pack_accepts_explicit_atlas_rect_and_rejects_invalid_rect(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw); theme = self.make_theme(root)
            config = {"app_banners": {"com.example.app": "banners/a.png#120,8,560,720"}}
            (theme / "config.json").write_text(json.dumps(config))
            accepted = self.run_tool("pack", theme, root / "rect.zip")
            self.assertEqual(accepted.returncode, 0, accepted.stderr)
            config["app_banners"]["com.example.app"] = "banners/a.png#4000,0,100,1"
            (theme / "config.json").write_text(json.dumps(config))
            rejected = self.run_tool("pack", theme, root / "bad-rect.zip")
            self.assertNotEqual(rejected.returncode, 0)
            self.assertIn("invalid value for config key: app_banners", rejected.stderr)

    def test_pack_accepts_array_banner_mapping_and_focus_overshoot(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw); theme = self.make_theme(root)
            (theme / "banners" / "cover.jpeg").write_bytes(b"jpeg")
            config = {"focus_overshoot": True, "app_banners": ["com.example.app=banners/cover.jpeg#1"]}
            (theme / "config.json").write_text(json.dumps(config))
            result = self.run_tool("pack", theme, root / "array.zip")
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_pack_carries_a_clock_font_and_coin_icons(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw); theme = self.make_theme(root)
            (theme / "fonts").mkdir(); (theme / "icons").mkdir(); (theme / "wallpapers").mkdir()
            (theme / "fonts" / "clock.ttf").write_bytes(b"\x00\x01\x00\x00" + b"\x00" * 12)
            (theme / "icons" / "coin-cap.webp").write_bytes(b"webp")
            (theme / "wallpapers" / "01-cap.jpg").write_bytes(b"jpeg")
            (theme / ".DS_Store").write_bytes(b"finder")
            config = {"clock_font": "fonts/clock.ttf", "clock_font_axes": {"wght": 700, "wdth": 120},
                      "wallpaper_coins": {"01-cap.jpg": "icons/coin-cap.webp"}}
            (theme / "config.json").write_text(json.dumps(config))
            archive = root / "clock.zip"
            packed = self.run_tool("pack", theme, archive)
            self.assertEqual(packed.returncode, 0, packed.stderr)
            inspected = self.run_tool("inspect", archive)
            self.assertEqual(inspected.returncode, 0, inspected.stderr)
            self.assertIn("fonts/clock.ttf, icons/coin-cap.webp", inspected.stdout)
            self.assertNotIn(".DS_Store", inspected.stdout)
            config["wallpaper_coins"]["01-cap.jpg"] = "coin_odyssey_cap"
            (theme / "config.json").write_text(json.dumps(config))
            rejected = self.run_tool("pack", theme, root / "resource-coin.zip")
            self.assertNotEqual(rejected.returncode, 0)
            self.assertIn("invalid value for config key: wallpaper_coins", rejected.stderr)

    def test_pack_rejects_legacy_manifest_id_and_noncanonical_zip_paths(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw); theme = self.make_theme(root)
            manifest = {"schema": 1, "id": "legacy", "name": "Reserved", "version": 1}
            (theme / "manifest.json").write_text(json.dumps(manifest))
            result = self.run_tool("pack", theme, root / "legacy.zip")
            self.assertNotEqual(result.returncode, 0); self.assertIn("reserved", result.stderr)
            for path in ("banners//a.png", "banners/./a.png"):
                archive = root / (path.replace("/", "-") + ".zip")
                with zipfile.ZipFile(archive, "w") as bundle:
                    bundle.writestr("manifest.json", '{"schema":1,"id":"safe","name":"Safe","version":1}')
                    bundle.writestr("config.json", '{}')
                    bundle.writestr(path, "bad")
                inspected = self.run_tool("inspect", archive)
                self.assertNotEqual(inspected.returncode, 0); self.assertIn("unsafe ZIP entry", inspected.stderr)

    def test_inspect_rejects_unsafe_archive_before_any_tv_operation(self):
        with tempfile.TemporaryDirectory() as raw:
            archive = Path(raw) / "bad.zip"
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("manifest.json", '{"schema":1,"id":"safe","name":"Safe","version":1}')
                bundle.writestr("config.json", '{}')
                bundle.writestr("../outside.jpg", "bad")
            result = self.run_tool("inspect", archive)
            self.assertNotEqual(result.returncode, 0); self.assertIn("unsafe ZIP entry", result.stderr)

    def test_invalid_package_rejected_by_network_commands(self):
        result = self.run_tool("list", "--package", "com.example;bad")
        self.assertEqual(result.returncode, 2)
        self.assertIn("not an Android package name", result.stderr)

    def test_list_decodes_the_real_bundle_without_consuming_its_closing_bracket(self):
        response = ('Result: Bundle[{themes=[{"id":"legacy","name":"原版主题","version":0,"active":true},'
                    '{"id":"animal-crossing","name":"动森 · 岛屿来信","version":1,"active":false},'
                    '{"id":"zelda-wilds","name":"塞尔达 · 旷野与天空","version":1,"active":false},'
                    '{"id":"mario-odyssey","name":"奥德赛 · 王国之旅","version":1,"active":false}], ok=true}]')
        original = THEME_PACK.content_call
        self.addCleanup(setattr, THEME_PACK, "content_call", original)
        THEME_PACK.content_call = lambda package, method: response
        visible = io.StringIO()
        with contextlib.redirect_stdout(visible):
            THEME_PACK.list_themes("com.ts.launcher")
        self.assertEqual(visible.getvalue().splitlines(), [
            "* legacy\t原版主题\t0",
            "  animal-crossing\t动森 · 岛屿来信\t1",
            "  zelda-wilds\t塞尔达 · 旷野与天空\t1",
            "  mario-odyssey\t奥德赛 · 王国之旅\t1",
        ])


if __name__ == "__main__":
    unittest.main()
