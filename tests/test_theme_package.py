"""tools/theme.sh and tools/theme.ps1 can push a theme to a launcher built with another applicationId.

`./gradlew assembleRelease -PlauncherApplicationId=com.ts.launcher` (the Xiaomi builds in docs/devices/)
moves the files directory, the ThemeProvider authority and the package to restart, but the Java namespace
and so the activity class stay com.gathin.tvhome. The package is pasted into the TV's shell, so anything
that is not a package name has to stop the script before tools/tv.py runs. tools/tv.py and b64push.py are
replaced by a fake that only records what the real scripts would send.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it; CI has no PowerShell.
"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
DEFAULT = "com.gathin.tvhome"
XIAOMI = "com.ts.launcher"
PWSH = shutil.which("pwsh")
INVALID = ["x; rm -rf /", "$(reboot)", "com.ts.launcher\n", "com.ts.launcher/x", "com", "1com.x",
           "com..x", "com.ts launcher"]

# Stands in for tools/tv.py and tools/b64push.py: records the arguments, sends nothing.
FAKE_TOOL = r'''
import json
import os
import sys

with open(os.environ["FAKE_TV_LOG"], "a") as f:
    f.write(json.dumps({"tool": os.path.basename(sys.argv[0]), "args": sys.argv[1:]}) + "\n")
'''

# Stand in for the TV's settings and am while the restart command runs on this machine.
STUB_SETTINGS = '#!/bin/sh\necho "settings $*" >> "$STUB_LOG"\n[ "$1" = get ] && echo "$FAKE_A11Y"\nexit 0\n'
STUB_AM = '#!/bin/sh\necho "am $*" >> "$STUB_LOG"\n'
STUB_SLEEP = '#!/bin/sh\n'


class ThemePackageTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.tools = self.base / "repo" / "tools"
        self.tools.mkdir(parents=True)
        for name in ("theme.sh", "theme.ps1"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        for name in ("tv.py", "b64push.py"):
            (self.tools / name).write_text(FAKE_TOOL)
        # The scripts call the tools venv's Python, which here is the real interpreter running the fakes.
        # theme.ps1 asks for the Windows path, which PowerShell resolves to venv/Scripts/python.exe here too.
        for client in (self.tools / "venv" / "bin" / "python",
                       self.tools / "venv" / "Scripts" / "python.exe"):
            client.parent.mkdir(parents=True, exist_ok=True)
            client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
            client.chmod(0o755)
        self.log = self.base / "tv-log.jsonl"
        self.theme = self.base / "aurora"
        (self.theme / "wallpapers").mkdir(parents=True)
        (self.theme / "config.json").write_text('{"columns": 6}\n')
        (self.theme / "wallpaper.jpg").write_bytes(b"jpg")
        (self.theme / "wallpapers" / "a.jpg").write_bytes(b"jpg")

    def entries(self):
        return ("sh", "ps1") if PWSH else ("sh",)

    def theme_args(self, entry, reset):
        if reset:
            return ["--reset"] if entry == "sh" else ["-Reset"]
        return [str(self.theme)]

    def run_theme(self, entry, args, package=None, param=None):
        """Runs the real script; package goes in LAUNCHER_PKG, param in theme.ps1's -Package."""
        env = {k: v for k, v in os.environ.items() if k != "LAUNCHER_PKG"}
        env.update(TV_HOST="192.168.1.50", FAKE_TV_LOG=str(self.log), HOME=str(self.base))
        if package is not None:
            env["LAUNCHER_PKG"] = package
        if entry == "sh":
            argv = ["bash", str(self.tools / "theme.sh"), *args]
        else:
            argv = [PWSH, "-NoProfile", "-File", str(self.tools / "theme.ps1"),
                    "-TvHost", "192.168.1.50", *(["-Package", param] if param is not None else []), *args]
        result = subprocess.run(argv, cwd=self.base, env=env, stdin=subprocess.DEVNULL,
                                capture_output=True, text=True, timeout=180)
        calls = []
        if self.log.exists():
            calls = [json.loads(line) for line in self.log.read_text().splitlines()]
            self.log.unlink()
        return result, calls

    def commands(self, calls):
        """The shell commands sent through tv.py (not its --push uploads)."""
        return [c["args"][-1] for c in calls if c["tool"] == "tv.py" and c["args"][0] != "--push"]

    def assert_targets(self, result, calls, pkg):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        text = "\n".join(self.commands(calls))
        self.assertEqual(set(re.findall(r"/sdcard/Android/data/([^/\s]+)/", text)), {pkg})
        self.assertEqual(set(re.findall(r"content://([^/\s]+)/", text)), {f"{pkg}.theme"})
        self.assertIn(f"/sdcard/Android/data/{pkg}/files/config.json", text)
        self.assertIn(f"content://{pkg}.theme/config.json", text)
        self.assertEqual(re.findall(r"am force-stop (\S+);", text), [pkg])
        self.assertEqual(re.findall(r"am start -n (\S+) ", text), [f"{pkg}/com.gathin.tvhome.MainActivity"])
        self.assertIn(f'case "$a" in *{pkg}/*)', text)

    def test_default_targets_com_gathin_tvhome(self):
        for entry in self.entries():
            for reset in (False, True):
                for package in (None, ""):
                    with self.subTest(entry=entry, reset=reset, package=package):
                        result, calls = self.run_theme(entry, self.theme_args(entry, reset), package)
                        self.assert_targets(result, calls, DEFAULT)
                        if not reset:
                            text = "\n".join(self.commands(calls))
                            self.assertIn(f"/sdcard/Android/data/{DEFAULT}/files/wallpapers/a.jpg", text)
                            self.assertIn("Undo with: tools", result.stdout)
                            self.assertNotIn("LAUNCHER_PKG", result.stdout)
                            self.assertNotIn("-Package", result.stdout)

    def test_launcher_pkg_targets_com_ts_launcher(self):
        cases = [("sh", XIAOMI, None)]
        if PWSH:
            cases += [("ps1", XIAOMI, None), ("ps1", None, XIAOMI)]
        for entry, package, param in cases:
            for reset in (False, True):
                with self.subTest(entry=entry, reset=reset, param=param):
                    result, calls = self.run_theme(entry, self.theme_args(entry, reset), package, param)
                    self.assert_targets(result, calls, XIAOMI)
                    if not reset:
                        text = "\n".join(self.commands(calls))
                        self.assertIn(f"/sdcard/Android/data/{XIAOMI}/files/wallpapers/a.jpg", text)
                        self.assertIn(f"content://{XIAOMI}.theme/wallpapers", text)
                        undo = (f"LAUNCHER_PKG={XIAOMI} tools/theme.sh --reset" if entry == "sh"
                                else f"-Reset -Package {XIAOMI}")
                        self.assertIn(undo, result.stdout)

    def test_invalid_package_stops_before_tv(self):
        cases = [("sh", value, None) for value in INVALID]
        if PWSH:
            cases += [("ps1", value, None) for value in INVALID]
            cases += [("ps1", None, value) for value in INVALID]
        for entry, package, param in cases:
            for reset in (False, True):
                with self.subTest(entry=entry, reset=reset, package=package, param=param):
                    result, calls = self.run_theme(entry, self.theme_args(entry, reset), package, param)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual(calls, [], "nothing may reach tv.py or b64push.py")
                    self.assertIn("not an Android package name", result.stdout + result.stderr)

    def restart(self, command, accessibility):
        """Runs the restart command with stand-ins for settings, am and sleep, and only those on PATH."""
        stubs = self.base / "stubs"
        stubs.mkdir(exist_ok=True)
        for name, body in (("settings", STUB_SETTINGS), ("am", STUB_AM), ("sleep", STUB_SLEEP)):
            (stubs / name).write_text(body)
            (stubs / name).chmod(0o755)
        stub_log = self.base / "stub-log.txt"
        stub_log.unlink(missing_ok=True)
        result = subprocess.run(["/bin/sh", "-c", command], stdin=subprocess.DEVNULL, capture_output=True,
                                text=True, timeout=60,
                                env={"PATH": str(stubs), "STUB_LOG": str(stub_log), "FAKE_A11Y": accessibility})
        self.assertEqual(result.returncode, 0, result.stderr)
        return stub_log.read_text().splitlines()

    def test_restart_reenables_only_its_own_accessibility_services(self):
        # Another applicationId's services keep their com.gathin.tvhome class names, so the match is on
        # the package before the slash.
        ours_ts = f"{XIAOMI}/com.gathin.tvhome.InputKeyService"
        ours_default = f"{DEFAULT}/.InputKeyService:com.example.other/.Service"
        cases = [(XIAOMI, ours_ts, True), (XIAOMI, ours_default, False), (XIAOMI, "null", False),
                 (DEFAULT, ours_default, True), (DEFAULT, ours_ts, False)]
        for entry in self.entries():
            for pkg, accessibility, reenabled in cases:
                with self.subTest(entry=entry, pkg=pkg, accessibility=accessibility):
                    result, calls = self.run_theme(entry, self.theme_args(entry, False), pkg)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    command = next(c for c in self.commands(calls) if "am force-stop" in c)
                    log = self.restart(command, accessibility)
                    self.assertEqual(log[:3], ["settings get secure enabled_accessibility_services",
                                               f"am force-stop {pkg}",
                                               f"am start -n {pkg}/com.gathin.tvhome.MainActivity"])
                    if reenabled:
                        self.assertEqual(log[-2:], [
                            f"settings put secure enabled_accessibility_services {accessibility}",
                            "settings put secure accessibility_enabled 1"])
                    else:
                        self.assertEqual(len(log), 3, log)


if __name__ == "__main__":
    unittest.main()
