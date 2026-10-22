#!/usr/bin/env python3
"""Build, inspect and install portable CleanYourTV theme packages.

Theme packages are deliberately data-only ZIP files.  This tool validates a package before
opening an ADB connection, so a malformed archive never reaches the TV.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tempfile
import uuid
import zipfile


MAX_COMPRESSED = 16 * 1024 * 1024
MAX_EXTRACTED = 48 * 1024 * 1024
MAX_FILE = 8 * 1024 * 1024
MAX_ENTRIES = 128
MAX_JSON = 64 * 1024
ID_RE = re.compile(r"[a-z0-9][a-z0-9-]{0,47}\Z")
PACKAGE_RE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+\Z")
IMAGE_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]*\.(?:jpg|jpeg|png|webp)\Z", re.I)
ATLAS_CELL_RE = re.compile(r"(?:[0-9]|1[0-5])\Z")
ATLAS_RECT_RE = re.compile(r"([0-9]+),([0-9]+),([0-9]+),([0-9]+)\Z")
ALLOWED_CONFIG = {
    "header_style", "background_color", "wallpaper", "wallpaper_rotation_minutes", "wallpaper_order",
    "wallpaper_dim", "wallpaper_names", "wallpaper_coins", "wallpaper_tints", "app_banners", "columns",
    "dock_columns", "rows_below_dock", "dock_opacity", "dock_glass", "dock_title", "dock_row",
    "dock_even_padding", "ambient_glow", "app_glow", "cinematic_home", "sky_intensity", "focus_overshoot",
    "always_show_names", "reduced_motion", "wallpaper_true_color",
}
STRING_CONFIG = {"header_style", "background_color", "wallpaper_order"}
BOOL_CONFIG = {"wallpaper", "dock_glass", "dock_title", "dock_row", "dock_even_padding", "ambient_glow",
               "app_glow", "cinematic_home", "always_show_names", "reduced_motion", "wallpaper_true_color",
               "focus_overshoot"}
MAP_OR_ARRAY = {"wallpaper_names", "wallpaper_coins", "wallpaper_tints"}
ROOT = Path(__file__).resolve().parent
DEFAULT_PACKAGE = "com.gathin.tvhome"


class ThemeError(Exception):
    pass


def load_json_bytes(raw, label):
    if len(raw) == 0 or len(raw) > MAX_JSON:
        raise ThemeError(f"{label} must be between 1 and {MAX_JSON} bytes")
    try:
        text = raw.decode("utf-8", "strict")
        return json.loads(text, object_pairs_hook=no_duplicate_keys)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ThemeError(f"invalid {label}: {error}") from error


def no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ThemeError("duplicate JSON key")
        result[key] = value
    return result


def allowed_path(name, is_dir=False):
    if not name or name.startswith("/") or "\\" in name or ".." in name:
        return False
    if is_dir:
        return name in {"wallpapers/", "banners/"}
    if name in {"manifest.json", "config.json", "wallpaper.jpg", "preview.jpg"}:
        return True
    # PurePosixPath normalises `banners//a.png` and `banners/./a.png`; Android rejects
    # both before it constructs a File, so check the raw slash count first.
    if name.count("/") != 1:
        return False
    path = PurePosixPath(name)
    return len(path.parts) == 2 and path.parts[0] in {"wallpapers", "banners"} and bool(IMAGE_RE.fullmatch(path.parts[1]))


def validate_manifest(manifest):
    if not isinstance(manifest, dict) or set(manifest) != {"schema", "id", "name", "version"}:
        raise ThemeError("manifest must contain exactly schema, id, name and version")
    if manifest["schema"] != 1 or isinstance(manifest["schema"], bool):
        raise ThemeError("manifest schema must be 1")
    if not isinstance(manifest["id"], str) or not ID_RE.fullmatch(manifest["id"]):
        raise ThemeError("manifest id must be lowercase letters, digits and hyphens")
    if manifest["id"] == "legacy":
        raise ThemeError("manifest id legacy is reserved")
    name = manifest["name"]
    if not isinstance(name, str) or not name.strip() or len(name) > 60 or any(ord(c) < 32 or ord(c) == 127 for c in name):
        raise ThemeError("manifest name must be readable and at most 60 characters")
    if not isinstance(manifest["version"], int) or isinstance(manifest["version"], bool) or not 1 <= manifest["version"] <= 2 ** 31 - 1:
        raise ThemeError("manifest version must be a positive 32-bit integer")


def valid_banner(value, names):
    if not isinstance(value, str):
        return False
    path, marker, cell = value.partition("#")
    if marker:
        if ATLAS_CELL_RE.fullmatch(cell):
            pass
        else:
            rect = ATLAS_RECT_RE.fullmatch(cell)
            if not rect:
                return False
            x, y, width, height = (int(part) for part in rect.groups())
            if x > 4096 or y > 4096 or not 0 < width <= 4096 or not 0 < height <= 4096 \
                    or x + width > 4096 or y + height > 4096:
                return False
    return path in names and path.startswith("banners/") and bool(IMAGE_RE.fullmatch(path[len("banners/"):]))


def banner_pairs(value):
    if isinstance(value, dict):
        return list(value.items())
    if isinstance(value, list):
        pairs = []
        for item in value:
            if not isinstance(item, str) or item.count("=") != 1:
                return None
            pairs.append(tuple(item.split("=", 1)))
        return pairs
    return None


def valid_banners(value, names):
    pairs = banner_pairs(value)
    if pairs is None:
        return False
    seen = set()
    for package, path in pairs:
        if not isinstance(package, str) or not PACKAGE_RE.fullmatch(package) or package in seen \
                or not valid_banner(path, names):
            return False
        seen.add(package)
    return True


def validate_config(config, names):
    if not isinstance(config, dict):
        raise ThemeError("config.json must be an object")
    unknown = set(config) - ALLOWED_CONFIG
    if unknown:
        raise ThemeError("config contains unsupported key: " + sorted(unknown)[0])
    for key, value in config.items():
        if key in STRING_CONFIG:
            good = isinstance(value, str)
        elif key in BOOL_CONFIG:
            good = isinstance(value, bool)
        elif key in MAP_OR_ARRAY:
            good = isinstance(value, (dict, list))
        elif key == "app_banners":
            good = valid_banners(value, names)
        else:
            good = isinstance(value, (int, float)) and not isinstance(value, bool)
        if not good:
            raise ThemeError("invalid value for config key: " + key)


def inspect_zip(path):
    path = Path(path)
    if not path.is_file():
        raise ThemeError(f"not a file: {path}")
    if path.stat().st_size > MAX_COMPRESSED:
        raise ThemeError(f"ZIP exceeds {MAX_COMPRESSED} byte limit")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            if len(infos) > MAX_ENTRIES:
                raise ThemeError(f"ZIP has more than {MAX_ENTRIES} entries")
            names, seen, total = set(), set(), 0
            payloads = {}
            for info in infos:
                name = info.filename
                if name in seen:
                    raise ThemeError("duplicate ZIP entry: " + name)
                seen.add(name)
                if info.is_dir():
                    if not allowed_path(name, True):
                        raise ThemeError("unsafe ZIP directory: " + name)
                    continue
                if not allowed_path(name) or name in names:
                    raise ThemeError("unsafe ZIP entry: " + name)
                names.add(name)
                if info.file_size > MAX_FILE or (name in {"manifest.json", "config.json"} and info.file_size > MAX_JSON):
                    raise ThemeError("ZIP entry too large: " + name)
                total += info.file_size
                if total > MAX_EXTRACTED:
                    raise ThemeError("ZIP payload exceeds extracted size limit")
                payloads[name] = archive.read(info)
    except zipfile.BadZipFile as error:
        raise ThemeError("invalid ZIP file") from error
    if {"manifest.json", "config.json"} - names:
        raise ThemeError("manifest.json and config.json are required")
    manifest = load_json_bytes(payloads["manifest.json"], "manifest.json")
    config = load_json_bytes(payloads["config.json"], "config.json")
    validate_manifest(manifest)
    validate_config(config, names)
    return {"manifest": manifest, "entries": sorted(names), "sha256": digest, "bytes": path.stat().st_size}


def files_from_folder(folder):
    folder = Path(folder)
    if not folder.is_dir() or folder.is_symlink():
        raise ThemeError(f"not a theme folder: {folder}")
    found = []
    for current, dirs, files in os.walk(folder, followlinks=False):
        current_path = Path(current)
        relative_dir = current_path.relative_to(folder).as_posix()
        if relative_dir != "." and relative_dir not in {"wallpapers", "banners"}:
            raise ThemeError("unsupported source directory: " + relative_dir)
        for child in dirs + files:
            if (current_path / child).is_symlink():
                raise ThemeError("symbolic links are not allowed: " + str(current_path / child))
        for filename in files:
            source = current_path / filename
            relative = source.relative_to(folder).as_posix()
            if not allowed_path(relative):
                raise ThemeError("unsupported source file: " + relative)
            if source.stat().st_size > MAX_FILE:
                raise ThemeError("source file too large: " + relative)
            found.append((relative, source))
    return sorted(found)


def pack(folder, output):
    files = files_from_folder(folder)
    names = {name for name, _ in files}
    if {"manifest.json", "config.json"} - names:
        raise ThemeError("manifest.json and config.json are required")
    payloads = {name: source.read_bytes() for name, source in files}
    manifest = load_json_bytes(payloads["manifest.json"], "manifest.json")
    config = load_json_bytes(payloads["config.json"], "config.json")
    validate_manifest(manifest)
    validate_config(config, names)
    total = sum(len(data) for data in payloads.values())
    if total > MAX_EXTRACTED:
        raise ThemeError("theme payload exceeds extracted size limit")
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(payloads):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, payloads[name])
    return inspect_zip(output)


def ensure_package(package):
    if not PACKAGE_RE.fullmatch(package):
        raise ThemeError("not an Android package name")
    return package


def package_type(package):
    try:
        return ensure_package(package)
    except ThemeError as error:
        raise argparse.ArgumentTypeError(str(error)) from error


def run_tv(command):
    return subprocess.run([sys.executable, str(ROOT / "tv.py"), command], text=True, capture_output=True)


def content_call(package, method, arg=None):
    command = f"content call --uri content://{package}.theme --method {method}"
    if arg is not None:
        command += " --arg " + arg
    result = run_tv(command)
    if result.returncode:
        raise ThemeError(result.stderr.strip() or result.stdout.strip() or "TV command failed")
    output = result.stdout + result.stderr
    if not re.search(r"(?:^|[,{\s])ok=(?:true|1)(?:[,}\s]|$)", output):
        raise ThemeError("TV rejected theme operation: " + output.strip()[:200])
    return output


def theme_id_from_output(output):
    match = re.search(r"(?:^|[,\s{])id=([a-z0-9][a-z0-9-]{0,47})(?:[,}\s]|$)", output)
    if not match:
        raise ThemeError("TV did not return an imported theme id")
    return match.group(1)


def install(path, package, activate):
    details = inspect_zip(path)  # Must happen before any command can connect to the TV.
    remote = f"/data/local/tmp/cleanyourtv-theme-{uuid.uuid4().hex}.zip"
    try:
        upload = subprocess.run([sys.executable, str(ROOT / "b64push.py"), str(path), remote], text=True, capture_output=True)
        if upload.returncode:
            raise ThemeError(upload.stderr.strip() or upload.stdout.strip() or "theme upload failed")
        write = run_tv(f"content write --uri content://{package}.theme/incoming.zip < {remote}")
        if write.returncode:
            raise ThemeError(write.stderr.strip() or write.stdout.strip() or "could not copy theme into launcher")
        imported = theme_id_from_output(content_call(package, "importTheme"))
        if activate:
            content_call(package, "activateTheme", imported)
        return details, imported
    finally:
        # This path is generated above and never derives from user input.
        cleanup = run_tv(f"rm -f {remote}")
        if cleanup.returncode:
            print("warning: could not remove temporary TV upload", file=sys.stderr)


def list_themes(package):
    output = content_call(package, "listThemes")
    marker = output.find("themes=")
    if marker < 0:
        raise ThemeError("TV did not return its theme list")
    try:
        themes, _ = json.JSONDecoder().raw_decode(output[marker + len("themes="):])
    except json.JSONDecodeError as error:
        raise ThemeError("TV returned an invalid theme list") from error
    if not isinstance(themes, list):
        raise ThemeError("TV returned an invalid theme list")
    for item in themes:
        if not isinstance(item, dict) or not isinstance(item.get("id"), str) \
                or (item["id"] != "legacy" and not ID_RE.fullmatch(item["id"])) \
                or not isinstance(item.get("name"), str) \
                or not isinstance(item.get("version"), int) or isinstance(item["version"], bool) \
                or item["version"] < 0 or not isinstance(item.get("active"), bool):
            raise ThemeError("TV returned an invalid theme list")
    for item in themes:
        print(f"{'*' if item.get('active') else ' '} {item.get('id')}\t{item.get('name')}\t{item.get('version')}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    def package_arg(command): command.add_argument("--package", default=DEFAULT_PACKAGE, type=package_type,
                                                     help="launcher package (default: %(default)s)")
    pack_cmd = commands.add_parser("pack", help="validate a folder and create a reproducible ZIP")
    pack_cmd.add_argument("folder", type=Path); pack_cmd.add_argument("output", type=Path)
    inspect_cmd = commands.add_parser("inspect", help="validate a local theme ZIP")
    inspect_cmd.add_argument("zip", type=Path)
    install_cmd = commands.add_parser("install", help="validate, upload and import a theme ZIP")
    install_cmd.add_argument("zip", type=Path); install_cmd.add_argument("--activate", action="store_true"); package_arg(install_cmd)
    list_cmd = commands.add_parser("list", help="list themes installed on the TV"); package_arg(list_cmd)
    activate_cmd = commands.add_parser("activate", help="activate an installed theme on the TV")
    activate_cmd.add_argument("id", type=lambda value: value if ID_RE.fullmatch(value) else (_ for _ in ()).throw(argparse.ArgumentTypeError("invalid theme id")))
    package_arg(activate_cmd)
    args = parser.parse_args(argv)
    try:
        if args.command == "pack":
            details = pack(args.folder, args.output); print(f"packed {args.output} ({details['bytes']} bytes, sha256 {details['sha256']})")
        elif args.command == "inspect":
            details = inspect_zip(args.zip); manifest = details["manifest"]
            print(f"{manifest['name']} ({manifest['id']} v{manifest['version']})\nsha256 {details['sha256']}\nentries: " + ", ".join(details["entries"]))
        elif args.command == "install":
            _, imported = install(args.zip, args.package, args.activate); print(f"imported {imported}" + (" and activated" if args.activate else ""))
        elif args.command == "list": list_themes(args.package)
        elif args.command == "activate": content_call(args.package, "activateTheme", args.id); print(f"activated {args.id}")
        return 0
    except ThemeError as error:
        print(f"theme-pack: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
